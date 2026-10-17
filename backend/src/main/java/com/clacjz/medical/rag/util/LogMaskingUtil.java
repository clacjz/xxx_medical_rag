package com.clacjz.medical.rag.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogMaskingUtil {

    private static final Pattern[] SENSITIVE_PATTERNS = {
            Pattern.compile("([Bb]earer\\s+)([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)"),
            Pattern.compile("(jwt|JWT|token|Token|TOKEN)[\\s:-]*([Bb]earer)?\\s*([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)"),
            Pattern.compile("(api[_-]?key|api_key|apikey|APIKEY)[\\s:=]+[\"']?([^\"',\\s]{8,})"),
            Pattern.compile("(secret[_-]?key|secret_key|SECRET_KEY)[\\s:=]+[\"']?([^\"',\\s]{4,})"),
            Pattern.compile("(password|passwd|pwd|PASSWORD|PASSWD)[\\s:=]+[\"']?([^\"',\\s]{4,})"),
            Pattern.compile("[\"']?(password|passwd|pwd|PASSWORD)[\"']?\\s*[:=]\\s*[\"']?([^\"',\\s]+)"),
            Pattern.compile("([Ss]ecret)[\\s:=]+[\"']?([^\"',\\s]{4,})"),
            Pattern.compile("(access[_-]?key|access_key|ACCESS_KEY)[\\s:=]+[\"']?([^\"',\\s]{8,})"),
            Pattern.compile("(sk-|AKIA|ghp_|gho_|npm_)[A-Za-z0-9]{10,}"),
            Pattern.compile("(authorization|Authorization|AUTHORIZATION)[\\s:-]+([^\"']+)"),
    };

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})\\d{4}(\\d{4})");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4})");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b(\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4})\\b");

    private static final int MAX_LOG_LENGTH = 10000;
    private static final int MASKED_VALUE_LENGTH = 8;

    private LogMaskingUtil() {
    }

    public static String maskSensitiveInfo(String logMessage) {
        if (logMessage == null || logMessage.isEmpty()) {
            return logMessage;
        }

        String masked = logMessage;

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            masked = maskPattern(masked, pattern);
        }

        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("****-****-****-$4");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("***@***.***");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("$1****$2");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("$1**********$2");

        if (masked.length() > MAX_LOG_LENGTH) {
            masked = masked.substring(0, MAX_LOG_LENGTH) + "... [TRUNCATED]";
        }

        return masked;
    }

    private static String maskPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder();

            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    if (isLikelySensitiveValue(group, pattern.pattern())) {
                        replacement.append(getMaskedValue(group));
                    } else {
                        replacement.append(group);
                    }
                }
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static boolean isLikelySensitiveValue(String value, String pattern) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        if (value.startsWith("Bearer ") || value.startsWith("bearer ")) {
            return true;
        }

        if (value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            return true;
        }

        if (value.matches("^(sk-|AKIA|ghp_|gho_|npm_).*")) {
            return true;
        }

        if (pattern.contains("key") && value.length() >= 8) {
            return true;
        }

        if (pattern.contains("password|passwd|pwd") && value.length() >= 4) {
            return true;
        }

        if (pattern.contains("secret") && value.length() >= 8) {
            return true;
        }

        return false;
    }

    private static String getMaskedValue(String value) {
        if (value == null) {
            return "***MASKED***";
        }

        if (value.startsWith("Bearer ") || value.startsWith("bearer ")) {
            return value.substring(0, 7) + "***MASKED***";
        }

        return "***MASKED***";
    }

    public static String maskPrompt(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return prompt;
        }

        if (prompt.length() > 500) {
            return prompt.substring(0, 500) + "... [TRUNCATED for security]";
        }

        return maskSensitiveInfo(prompt);
    }

    public static String maskHeaders(String headers) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }

        String masked = headers;

        masked = Pattern.compile("([Aa]uthorization)[\\s:-]+([^\"']*)")
                .matcher(masked)
                .replaceAll("$1: ***MASKED***");

        masked = Pattern.compile("([Xx]-?[Aa]pi-[Kk]ey)[\\s:-]+([^\"']*)")
                .matcher(masked)
                .replaceAll("$1: ***MASKED***");

        masked = Pattern.compile("([Cc]ookie)[\\s:-]+([^\"']*)")
                .matcher(masked)
                .replaceAll("$1: ***MASKED***");

        return masked;
    }

    public static String maskUrlWithCredentials(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String masked = url;

        masked = masked.replaceAll("(://)([^:]+):([^@]+)(@)", "$1***MASKED***:***MASKED***$4");

        return masked;
    }
}
