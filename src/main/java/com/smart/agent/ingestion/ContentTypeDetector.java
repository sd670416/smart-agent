package com.smart.agent.ingestion;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public final class ContentTypeDetector {
    private static final Pattern MARKDOWN = Pattern.compile(
            "(?m)^(#{1,6}\\s+\\S|[-*+]\\s+\\S|```|>\\s+\\S)|\\[[^]]+]\\([^)]+\\)");
    private static final Set<String> EXECUTABLE_MEDIA_TYPES = Set.of(
            "application/x-dosexec", "application/x-msdownload", "application/x-executable",
            "application/x-elf", "application/x-mach-binary", "application/x-sh");
    private static final Map<String, DetectedContentType> EXPECTED_EXTENSIONS = Map.ofEntries(
            Map.entry("pdf", DetectedContentType.PDF),
            Map.entry("docx", DetectedContentType.DOCX),
            Map.entry("xlsx", DetectedContentType.XLSX),
            Map.entry("md", DetectedContentType.MARKDOWN),
            Map.entry("markdown", DetectedContentType.MARKDOWN),
            Map.entry("txt", DetectedContentType.TEXT),
            Map.entry("png", DetectedContentType.IMAGE),
            Map.entry("jpg", DetectedContentType.IMAGE),
            Map.entry("jpeg", DetectedContentType.IMAGE),
            Map.entry("gif", DetectedContentType.IMAGE),
            Map.entry("bmp", DetectedContentType.IMAGE),
            Map.entry("tif", DetectedContentType.IMAGE),
            Map.entry("tiff", DetectedContentType.IMAGE),
            Map.entry("webp", DetectedContentType.IMAGE),
            Map.entry("exe", DetectedContentType.EXECUTABLE),
            Map.entry("dll", DetectedContentType.EXECUTABLE),
            Map.entry("com", DetectedContentType.EXECUTABLE),
            Map.entry("bat", DetectedContentType.EXECUTABLE),
            Map.entry("cmd", DetectedContentType.EXECUTABLE),
            Map.entry("sh", DetectedContentType.EXECUTABLE));

    private final Tika tika = new Tika();

    public Detection detect(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
        String mediaType = detectMediaType(bytes);
        DetectedContentType type = executableSignature(bytes)
                ? DetectedContentType.EXECUTABLE : map(mediaType, bytes);
        if (type == DetectedContentType.EXECUTABLE) {
            return new Detection(type, mediaType, true, "EXECUTABLE_CONTENT");
        }
        DetectedContentType expected = EXPECTED_EXTENSIONS.get(extension(filename));
        if (expected == DetectedContentType.EXECUTABLE
                || (expected != null && !compatible(expected, type))) {
            return new Detection(type, mediaType, true, "CONTENT_TYPE_MISMATCH");
        }
        return new Detection(type, mediaType, false, null);
    }

    private String detectMediaType(byte[] bytes) {
        try (TikaInputStream input = TikaInputStream.get(bytes)) {
            return tika.getDetector().detect(input, new Metadata()).toString();
        } catch (IOException exception) {
            throw new DocumentParseException("TYPE_DETECTION_FAILED", "Content type detection failed", exception);
        }
    }

    private static DetectedContentType map(String mediaType, byte[] bytes) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        if (EXECUTABLE_MEDIA_TYPES.contains(normalized)) return DetectedContentType.EXECUTABLE;
        if (normalized.equals("application/pdf")) return DetectedContentType.PDF;
        if (normalized.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return DetectedContentType.DOCX;
        }
        if (normalized.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return DetectedContentType.XLSX;
        }
        if (normalized.startsWith("image/")) return DetectedContentType.IMAGE;
        if (normalized.equals("text/markdown")) return DetectedContentType.MARKDOWN;
        if (normalized.startsWith("text/") || isUtf8Text(bytes)) {
            String text = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return MARKDOWN.matcher(text).find() ? DetectedContentType.MARKDOWN : DetectedContentType.TEXT;
        }
        return DetectedContentType.UNKNOWN;
    }

    private static boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = Byte.toUnsignedInt(value);
            if (unsigned == 0 || (unsigned < 0x20 && unsigned != '\n' && unsigned != '\r' && unsigned != '\t')) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ignored) {
            return false;
        }
    }

    private static boolean executableSignature(byte[] bytes) {
        return startsWith(bytes, 0x4d, 0x5a)
                || startsWith(bytes, 0x7f, 0x45, 0x4c, 0x46)
                || startsWith(bytes, 0xca, 0xfe, 0xba, 0xbe)
                || startsWith(bytes, 0xfe, 0xed, 0xfa, 0xce)
                || startsWith(bytes, 0xfe, 0xed, 0xfa, 0xcf)
                || startsWith(bytes, 0xcf, 0xfa, 0xed, 0xfe)
                || startsWith(bytes, 0xce, 0xfa, 0xed, 0xfe)
                || startsWith(bytes, '#', '!');
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != signature[index]) return false;
        }
        return true;
    }

    private static boolean compatible(DetectedContentType expected, DetectedContentType actual) {
        if ((expected == DetectedContentType.TEXT || expected == DetectedContentType.MARKDOWN)
                && (actual == DetectedContentType.TEXT || actual == DetectedContentType.MARKDOWN)) {
            return true;
        }
        return expected == actual;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record Detection(
            DetectedContentType type, String mediaType, boolean quarantined, String failureCode) {
    }
}
