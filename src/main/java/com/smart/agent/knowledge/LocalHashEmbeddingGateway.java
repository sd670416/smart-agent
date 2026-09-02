package com.smart.agent.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class LocalHashEmbeddingGateway implements EmbeddingGateway {
    public static final int DIMENSIONS = 64;
    private static final String MODEL_KEY = "local-hash-v1";

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        byte[] hash = digest(text);
        double squaredNorm = 0;
        double[] values = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            values[i] = (Byte.toUnsignedInt(hash[i]) - 127.5) / 127.5;
            squaredNorm += values[i] * values[i];
        }
        double norm = Math.sqrt(squaredNorm);
        List<Float> result = new ArrayList<>(DIMENSIONS);
        for (double value : values) {
            result.add((float) (value / norm));
        }
        return List.copyOf(result);
    }

    @Override
    public String modelKey() {
        return MODEL_KEY;
    }

    private static byte[] digest(String text) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 is unavailable", exception);
        }
    }
}
