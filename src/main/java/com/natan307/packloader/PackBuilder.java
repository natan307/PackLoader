package com.natan307.packloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Merges every pack in plugins/PackLoader/packs/ (zip files and folders), adds generated
 * item definitions and writes one deterministic zip into plugins/PackLoader/generated/.
 */
public final class PackBuilder {

    public record Settings(String namespace, int packFormat, String description,
                           boolean optimize, boolean stripMetadata, Collection<GuiDef> guis) {
    }

    private interface Source {
        InputStream open() throws IOException;
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Files whose JSON content is merged instead of overwritten when two packs contain them. */
    private static final Pattern MERGEABLE = Pattern.compile(
            "assets/[^/]+/(lang/.+|font/.+|atlases/.+)\\.json"
                    + "|assets/[^/]+/sounds\\.json"
                    + "|assets/minecraft/models/item/.+\\.json");

    private static final Pattern VALID_ASSET = Pattern.compile("assets/[a-z0-9_.-]+/[a-z0-9/._-]+");

    private final PackLoader plugin;
    private final Logger log;
    private int invalidNames;

    public PackBuilder(PackLoader plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public synchronized BuiltPack build(Collection<CustomItem> items, Settings settings) throws IOException {
        invalidNames = 0;
        Map<String, Source> files = new TreeMap<>();
        List<ZipFile> openZips = new ArrayList<>();
        try {
            Path data = plugin.getDataFolder().toPath();
            Path packsDir = data.resolve("packs");
            Files.createDirectories(packsDir);

            List<Path> sources;
            try (Stream<Path> s = Files.list(packsDir)) {
                sources = s.filter(p -> !p.getFileName().toString().startsWith("."))
                        .sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            }

            int packCount = 0;
            for (Path p : sources) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    addDirectory(files, p);
                    packCount++;
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    if (addZip(files, openZips, p)) {
                        packCount++;
                    }
                }
            }
            if (packCount == 0) {
                log.info("No packs found in plugins/PackLoader/packs/ - building a pack with generated content only.");
            } else {
                log.info("Merged " + packCount + " pack(s).");
            }

            Map<String, Integer> advances = addGenerated(files, items, settings);

            Path customMeta = data.resolve("pack.mcmeta");
            if (Files.isRegularFile(customMeta)) {
                files.put("pack.mcmeta", () -> Files.newInputStream(customMeta));
            } else {
                files.put("pack.mcmeta", bytes(GSON.toJson(mcmeta(settings))));
            }
            Path customIcon = data.resolve("pack.png");
            if (Files.isRegularFile(customIcon)) {
                files.put("pack.png", () -> Files.newInputStream(customIcon));
            }

            validateItems(files, items);
            if (invalidNames > 10) {
                log.warning("... and " + (invalidNames - 10) + " more invalid resource path(s).");
            }
            return write(files, settings, advances);
        } finally {
            for (ZipFile z : openZips) {
                try {
                    z.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    // ------------------------------------------------------------------ sources

    private void addDirectory(Map<String, Source> files, Path root) throws IOException {
        Map<String, Path> found = new LinkedHashMap<>();
        List<Path> all;
        try (Stream<Path> walk = Files.walk(root)) {
            all = walk.filter(Files::isRegularFile).toList();
        }
        for (Path p : all) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            found.put(rel, p);
        }
        String prefix = detectPrefix(found.keySet());
        for (Map.Entry<String, Path> e : found.entrySet()) {
            String name = clean(e.getKey(), prefix);
            if (name == null) {
                continue;
            }
            Path path = e.getValue();
            put(files, name, () -> Files.newInputStream(path));
        }
    }

    private boolean addZip(Map<String, Source> files, List<ZipFile> openZips, Path zipPath) {
        final ZipFile zf;
        try {
            zf = new ZipFile(zipPath.toFile());
        } catch (IOException ex) {
            log.warning("Skipping invalid zip '" + zipPath.getFileName() + "': " + ex.getMessage());
            return false;
        }
        openZips.add(zf);

        Map<String, ZipEntry> found = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (!ze.isDirectory()) {
                found.put(ze.getName().replace('\\', '/'), ze);
            }
        }
        String prefix = detectPrefix(found.keySet());
        for (Map.Entry<String, ZipEntry> e : found.entrySet()) {
            String name = clean(e.getKey(), prefix);
            if (name == null) {
                continue;
            }
            ZipEntry ze = e.getValue();
            put(files, name, () -> zf.getInputStream(ze));
        }
        return true;
    }

    /** Finds the folder that actually contains pack.mcmeta / assets (people often zip the parent folder). */
    private static String detectPrefix(Collection<String> names) {
        for (String n : names) {
            if (n.equals("pack.mcmeta") || n.startsWith("assets/")) {
                return "";
            }
        }
        final String meta = "/pack.mcmeta";
        for (String n : names) {
            if (n.startsWith("__MACOSX/")) {
                continue;
            }
            int i = n.indexOf("/assets/");
            if (i >= 0) {
                return n.substring(0, i + 1);
            }
            int slash = n.indexOf('/');
            if (slash >= 0 && n.endsWith(meta) && slash == n.length() - meta.length()) {
                return n.substring(0, slash + 1);
            }
        }
        return "";
    }

    /** Returns the cleaned entry name or null if the file must be ignored. */
    private String clean(String raw, String prefix) {
        String n = raw;
        if (!prefix.isEmpty()) {
            if (!n.startsWith(prefix)) {
                return null;
            }
            n = n.substring(prefix.length());
        }
        if (n.isEmpty() || n.indexOf(':') >= 0 || n.equalsIgnoreCase("pack.mcmeta")) {
            return null;
        }
        for (String seg : n.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")
                    || seg.equals("__MACOSX") || seg.equals(".git") || seg.equals(".DS_Store")
                    || seg.equalsIgnoreCase("Thumbs.db") || seg.equalsIgnoreCase("desktop.ini")) {
                return null;
            }
        }
        if (n.startsWith("assets/") && !VALID_ASSET.matcher(n).matches()) {
            invalidNames++;
            if (invalidNames <= 10) {
                log.warning("Invalid resource path (must be lowercase a-z 0-9 / . _ -): " + n);
            }
        }
        return n;
    }

    private void put(Map<String, Source> files, String name, Source src) {
        Source existing = files.get(name);
        if (existing != null && MERGEABLE.matcher(name).matches()) {
            try {
                JsonObject merged = mergeJson(readJson(existing), readJson(src));
                files.put(name, bytes(GSON.toJson(merged)));
                return;
            } catch (Exception ex) {
                log.warning("Could not merge '" + name + "' (" + ex.getMessage() + ") - later pack wins.");
            }
        }
        files.put(name, src);
    }

    private static JsonObject readJson(Source src) throws IOException {
        try (Reader r = new InputStreamReader(src.open(), StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) {
                throw new IOException("not a JSON object");
            }
            return el.getAsJsonObject();
        }
    }

    private static JsonObject mergeJson(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> e : over.entrySet()) {
            String key = e.getKey();
            JsonElement value = e.getValue();
            JsonElement current = out.get(key);
            boolean listKey = key.equals("providers") || key.equals("sources") || key.equals("overrides");
            if (listKey && current != null && current.isJsonArray() && value.isJsonArray()) {
                JsonArray joined = current.getAsJsonArray().deepCopy();
                joined.addAll(value.getAsJsonArray().deepCopy());
                out.add(key, joined);
            } else {
                out.add(key, value.deepCopy());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- generated

    private Map<String, Integer> addGenerated(Map<String, Source> files, Collection<CustomItem> items, Settings st) {
        String ns = st.namespace();
        for (CustomItem item : items) {
            if (!item.generate()) {
                continue;
            }
            String modelRef;
            if (item.texture() != null) {
                modelRef = ns + ":packloader_generated/" + item.id();
                String parent = item.parent() == null ? "generated" : item.parent().trim().toLowerCase(Locale.ROOT);
                if (!parent.contains(":")) {
                    parent = "minecraft:item/" + parent;
                }
                JsonObject model = new JsonObject();
                model.addProperty("parent", parent);
                JsonObject textures = new JsonObject();
                textures.addProperty("layer0", item.texture());
                model.add("textures", textures);
                files.put("assets/" + ns + "/models/packloader_generated/" + item.id() + ".json",
                        bytes(GSON.toJson(model)));
            } else {
                modelRef = item.model();
            }

            JsonObject inner = new JsonObject();
            inner.addProperty("type", "minecraft:model");
            inner.addProperty("model", modelRef);
            JsonObject definition = new JsonObject();
            definition.add("model", inner);
            files.put("assets/" + ns + "/items/" + item.id() + ".json", bytes(GSON.toJson(definition)));
        }
        return addGuiFont(files, st);
    }

    /** Generates assets/&lt;ns&gt;/font/gui.json so GUI backgrounds can be drawn in inventory titles. */
    private Map<String, Integer> addGuiFont(Map<String, Source> files, Settings st) {
        Map<String, Integer> advances = new LinkedHashMap<>();
        if (st.guis().isEmpty()) {
            return advances;
        }
        String ns = st.namespace();
        JsonArray providers = new JsonArray();

        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", Spacing.advances());
        providers.add(space);

        for (GuiDef gui : st.guis()) {
            if (gui.background() == null) {
                continue;
            }
            String loc = gui.background();
            int i = loc.indexOf(':');
            String bns = i < 0 ? "minecraft" : loc.substring(0, i);
            String path = i < 0 ? loc : loc.substring(i + 1);
            String file = "assets/" + bns + "/textures/" + path + ".png";
            Source src = files.get(file);
            if (src == null) {
                log.warning("GUI '" + gui.id() + "': background texture not found in any pack: " + file);
                continue;
            }
            int[] size = pngSize(src);
            if (size == null) {
                log.warning("GUI '" + gui.id() + "': background is not a valid PNG: " + file);
                continue;
            }
            JsonObject bitmap = new JsonObject();
            bitmap.addProperty("type", "bitmap");
            bitmap.addProperty("file", loc + ".png");
            // baseline of an inventory title is 13px below the top edge of the GUI
            bitmap.addProperty("ascent", Math.min(size[1], 13 - gui.offsetY()));
            bitmap.addProperty("height", size[1]);
            JsonArray chars = new JsonArray();
            chars.add(String.valueOf(gui.glyph()));
            bitmap.add("chars", chars);
            providers.add(bitmap);
            advances.put(gui.id(), size[0] + 1);
        }

        JsonObject font = new JsonObject();
        font.add("providers", providers);
        put(files, "assets/" + ns + "/font/gui.json", bytes(GSON.toJson(font)));
        return advances;
    }

    private static int[] pngSize(Source src) {
        try (InputStream in = src.open()) {
            byte[] head = in.readNBytes(24);
            if (head.length < 24 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
                return null;
            }
            int w = ((head[16] & 0xff) << 24) | ((head[17] & 0xff) << 16) | ((head[18] & 0xff) << 8) | (head[19] & 0xff);
            int h = ((head[20] & 0xff) << 24) | ((head[21] & 0xff) << 16) | ((head[22] & 0xff) << 8) | (head[23] & 0xff);
            return (w > 0 && h > 0) ? new int[]{w, h} : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private void validateItems(Map<String, Source> files, Collection<CustomItem> items) {
        for (CustomItem item : items) {
            if (item.generate()) {
                if (item.texture() != null) {
                    check(files, item.texture(), "textures", ".png", item.id(), "texture");
                } else if (item.model() != null) {
                    check(files, item.model(), "models", ".json", item.id(), "model");
                }
            } else if (item.itemModel() != null) {
                check(files, item.itemModel(), "items", ".json", item.id(), "item definition");
            }
        }
    }

    private void check(Map<String, Source> files, String location, String folder, String ext,
                       String itemId, String what) {
        int i = location.indexOf(':');
        String ns = i < 0 ? "minecraft" : location.substring(0, i);
        String path = i < 0 ? location : location.substring(i + 1);
        if (ns.equals("minecraft")) {
            return;
        }
        String file = "assets/" + ns + "/" + folder + "/" + path + ext;
        if (!files.containsKey(file)) {
            log.warning("Item '" + itemId + "': " + what + " not found in any pack: " + file);
        }
    }

    private static JsonObject mcmeta(Settings st) {
        // Wide supported range so the pack never shows an "incompatible" warning on 1.21.x - 26.x.
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", 34);
        range.addProperty("max_inclusive", 999);

        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", st.packFormat());
        pack.add("supported_formats", range);
        pack.addProperty("min_format", 34);
        pack.addProperty("max_format", 999);
        pack.addProperty("description", st.description());

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    private static Source bytes(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(b);
    }

    // -------------------------------------------------------------------- write

    private BuiltPack write(Map<String, Source> files, Settings settings, Map<String, Integer> advances) throws IOException {
        Path outDir = plugin.getDataFolder().toPath().resolve("generated");
        Files.createDirectories(outDir);
        Path tmp = outDir.resolve("pack.tmp");

        long saved = 0;
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        try (OutputStream fos = Files.newOutputStream(tmp);
             DigestOutputStream dos = new DigestOutputStream(new BufferedOutputStream(fos), md);
             ZipOutputStream zos = new ZipOutputStream(dos, StandardCharsets.UTF_8)) {
            zos.setLevel(9);
            for (Map.Entry<String, Source> e : files.entrySet()) {
                String name = e.getKey();
                ZipEntry ze = new ZipEntry(name);
                ze.setTimeLocal(LocalDateTime.of(2024, 1, 1, 0, 0)); // deterministic -> stable SHA-1
                zos.putNextEntry(ze);
                String lower = name.toLowerCase(Locale.ROOT);
                boolean isPng = lower.endsWith(".png");
                boolean isJson = lower.endsWith(".json") || lower.endsWith(".mcmeta");
                if (settings.optimize() && (isPng || isJson)) {
                    byte[] original;
                    try (InputStream in = e.getValue().open()) {
                        original = in.readAllBytes();
                    }
                    byte[] optimized = isPng ? PackOptimizer.png(original, settings.stripMetadata())
                            : PackOptimizer.json(original);
                    saved += original.length - optimized.length;
                    zos.write(optimized);
                } else {
                    try (InputStream in = e.getValue().open()) {
                        in.transferTo(zos);
                    }
                }
                zos.closeEntry();
            }
        }

        byte[] sha1 = md.digest();
        String hex = HexFormat.of().formatHex(sha1);
        Path out = outDir.resolve("pack-" + hex + ".zip");
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);

        try (Stream<Path> s = Files.list(outDir)) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("pack-") && n.endsWith(".zip") && !p.equals(out)) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // still being downloaded / locked - harmless
                    }
                }
            }
        }
        return new BuiltPack(out, sha1, hex, Files.size(out), files.size(), Map.copyOf(advances), Math.max(0, saved));
    }
}
