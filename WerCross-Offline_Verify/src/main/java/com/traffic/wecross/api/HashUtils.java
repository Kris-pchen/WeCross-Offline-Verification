package com.traffic.wecross.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class HashUtils {
    private HashUtils() {
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String sha256Hex(String input) {
        return hex(sha256(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String value) {
        if (value == null || !value.matches("(?i)^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("merkleRoot must be a 64-character hexadecimal SHA-256 value");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return bytes;
    }
    public static List<String> hexList(List<byte[]> bytesList) {
        List<String> result = new ArrayList<>();
        if (bytesList == null) {
            return result;
        }
        for (byte[] bytes : bytesList) {
            result.add(hex(bytes));
        }
        return result;
    }
}

