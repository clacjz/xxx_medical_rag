package com.clacjz.medical.rag.util;

import java.util.regex.Pattern;

/**
 * Utility for filename sanitization and extension extraction.
 */
public final class FileNameUtil {

    private static final Pattern PATH_TRAVERSAL = Pattern.compile("[/\\\\]\\.\\.[/\\\\]");
    private static final Pattern MULTIPLE_SLASHES = Pattern.compile("[/\\\\]{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1f\\x7f]");
    private static final Pattern SLASHES = Pattern.compile("[/\\\\]");

    private FileNameUtil() {}

    /**
     * Extract the file extension from a filename.
     */
    public static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Sanitize a filename to prevent path traversal and other injection attacks.
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        String sanitized = PATH_TRAVERSAL.matcher(filename).replaceAll("");
        sanitized = sanitized.replaceAll("\\.\\.[/\\\\]", "");
        sanitized = MULTIPLE_SLASHES.matcher(sanitized).replaceAll("/");
        sanitized = SLASHES.matcher(sanitized).replaceAll("_");
        sanitized = CONTROL_CHARS.matcher(sanitized).replaceAll("");
        if (sanitized.isBlank() || sanitized.equals("_")) {
            return "unnamed_file";
        }
        return sanitized;
    }
}