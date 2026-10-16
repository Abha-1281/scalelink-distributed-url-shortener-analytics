package com.scalelink.urlservice.domain.service;

/**
 * High-performance Bidirectional Base62 Encoder.
 * Translates 64-bit Snowflake unique identifiers into compact URL-friendly strings.
 * Character set: 0-9 (10) + a-z (26) + A-Z (26) = 62 characters.
 */
public class Base62Encoder {

    private static final String BASE62_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62_CHARS.length();

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(BASE62_CHARS.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        long temp = value;
        while (temp > 0) {
            int remainder = (int) (temp % BASE);
            sb.append(BASE62_CHARS.charAt(remainder));
            temp /= BASE;
        }

        return sb.reverse().toString();
    }

    public long decode(String str) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException("Base62 string cannot be null or empty");
        }

        long result = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = BASE62_CHARS.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            result = result * BASE + index;
        }

        return result;
    }
}
