package com.natan307.packloader;

import com.google.gson.JsonObject;

/** Negative/positive pixel-shift characters used to position custom GUI backgrounds in inventory titles. */
final class Spacing {

    private static final char NEG_BASE = '';
    private static final char POS_BASE = '';
    private static final int BITS = 12; // up to 4095 px

    private Spacing() {
    }

    /** Returns characters that move the text cursor by the given number of pixels. */
    static String shift(int px) {
        if (px == 0) {
            return "";
        }
        boolean negative = px < 0;
        int value = Math.min(Math.abs(px), (1 << BITS) - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BITS; i++) {
            if ((value & (1 << i)) != 0) {
                sb.append((char) ((negative ? NEG_BASE : POS_BASE) + i));
            }
        }
        return sb.toString();
    }

    /** The "advances" object of the font space provider. */
    static JsonObject advances() {
        JsonObject o = new JsonObject();
        for (int i = 0; i < BITS; i++) {
            o.addProperty(String.valueOf((char) (NEG_BASE + i)), -(1 << i));
            o.addProperty(String.valueOf((char) (POS_BASE + i)), (1 << i));
        }
        return o;
    }
}
