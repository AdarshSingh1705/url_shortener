package com.shortlink.util;

/**
 * Encodes a numeric database id into a short, URL-safe base62 string and back.
 * <p>
 * Using the row's own auto-increment id (rather than a random string) means every
 * code is unique by construction — no collision checks or retry loops needed.
 * A freshly created row (id=1) becomes "1", id=125 becomes "21", and so on;
 * codes grow one character roughly every 62x increase in traffic.
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    public static String encode(long id) {
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(ALPHABET.charAt(remainder));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid base62 character: " + c);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
