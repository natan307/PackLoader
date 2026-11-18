package com.natan307.packloader;

import java.nio.file.Path;
import java.util.Map;

/**
 * Result of a pack build.
 *
 * @param glyphAdvances width (in pixels) of each GUI background glyph, keyed by GUI id
 * @param savedBytes    bytes removed by the optimizer
 */
public record BuiltPack(
        Path file,
        byte[] sha1,
        String sha1Hex,
        long size,
        int fileCount,
        Map<String, Integer> glyphAdvances,
        long savedBytes) {
}
