package com.natan307.packloader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Lossless size optimisations for pack files. Every method returns the original bytes when it cannot do better. */
final class PackOptimizer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private PackOptimizer() {
    }

    /** Removes whitespace from JSON files. */
    static byte[] json(byte[] in) {
        try {
            String text = new String(in, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿') {
                text = text.substring(1);
            }
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject() && !el.isJsonArray()) {
                return in;
            }
            byte[] out = GSON.toJson(el).getBytes(StandardCharsets.UTF_8);
            return out.length < in.length ? out : in;
        } catch (Exception ex) {
            return in;
        }
    }

    /**
     * Lossless PNG optimisation: re-deflates the image data at maximum compression and
     * (optionally) drops ancillary chunks such as text, time and colour-profile metadata.
     * Pixels are never touched.
     */
    static byte[] png(byte[] in, boolean stripMetadata) {
        try {
            if (in.length < 20) {
                return in;
            }
            for (int i = 0; i < PNG_SIG.length; i++) {
                if (in[i] != PNG_SIG[i]) {
                    return in;
                }
            }

            List<String> types = new ArrayList<>();
            List<byte[]> bodies = new ArrayList<>();
            ByteArrayOutputStream idat = new ByteArrayOutputStream();
            int pos = 8;
            boolean ended = false;
            while (pos + 12 <= in.length && !ended) {
                int len = ((in[pos] & 0xff) << 24) | ((in[pos + 1] & 0xff) << 16)
                        | ((in[pos + 2] & 0xff) << 8) | (in[pos + 3] & 0xff);
                if (len < 0 || pos + 12L + len > in.length) {
                    return in;
                }
                String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
                byte[] body = Arrays.copyOfRange(in, pos + 8, pos + 8 + len);
                pos += 12 + len;
                if (type.equals("IDAT")) {
                    idat.writeBytes(body);
                }
                types.add(type);
                bodies.add(body);
                if (type.equals("IEND")) {
                    ended = true;
                }
            }
            if (!ended || idat.size() == 0) {
                return in;
            }

            byte[] oldIdat = idat.toByteArray();
            byte[] buf = new byte[65536];

            Inflater inflater = new Inflater();
            ByteArrayOutputStream raw = new ByteArrayOutputStream(Math.max(1024, oldIdat.length * 3));
            inflater.setInput(oldIdat);
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n > 0) {
                    raw.write(buf, 0, n);
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    inflater.end();
                    return in; // truncated / corrupt stream - leave the file alone
                }
            }
            inflater.end();

            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, false);
            deflater.setInput(raw.toByteArray());
            deflater.finish();
            ByteArrayOutputStream comp = new ByteArrayOutputStream(oldIdat.length);
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                comp.write(buf, 0, n);
            }
            deflater.end();
            byte[] newIdat = comp.size() < oldIdat.length ? comp.toByteArray() : oldIdat;

            ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
            out.writeBytes(PNG_SIG);
            boolean wroteIdat = false;
            for (int i = 0; i < types.size(); i++) {
                String t = types.get(i);
                if (t.equals("IDAT")) {
                    if (!wroteIdat) {
                        writeChunk(out, "IDAT", newIdat);
                        wroteIdat = true;
                    }
                    continue;
                }
                boolean critical = Character.isUpperCase(t.charAt(0));
                if (!critical && stripMetadata && !t.equals("tRNS")) {
                    continue;
                }
                writeChunk(out, t, bodies.get(i));
            }
            byte[] result = out.toByteArray();
            return result.length < in.length ? result : in;
        } catch (Exception ex) {
            return in;
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        out.writeBytes(be(body.length));
        out.writeBytes(t);
        out.writeBytes(body);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(body);
        out.writeBytes(be((int) crc.getValue()));
    }

    private static byte[] be(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }
}
