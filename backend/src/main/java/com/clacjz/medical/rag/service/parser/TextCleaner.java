package com.clacjz.medical.rag.service.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TextCleaner {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern MULTIPLE_TABS = Pattern.compile("\\t{2,}");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern WIDE_SPACES = Pattern.compile("[\\u3000\\u00A0\\u2000-\\u200B\\u2028\\u2029]");

    public String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        result = SPECIAL_CHARS.matcher(result).replaceAll("");
        result = WIDE_SPACES.matcher(result).replaceAll(" ");
        result = MULTIPLE_TABS.matcher(result).replaceAll("\t");
        result = MULTIPLE_NEWLINES.matcher(result).replaceAll("\n\n");
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");

        result = result.trim();

        return result;
    }

    public List<String> cleanLines(List<String> lines) {
        if (lines == null) {
            return new ArrayList<>();
        }

        return lines.stream()
                .map(this::clean)
                .filter(line -> !line.isBlank())
                .toList();
    }

    public List<String> splitIntoParagraphs(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        return Pattern.compile("\\n\\s*\\n")
                .splitAsStream(text)
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
    }

    public List<String> splitIntoSentences(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        String[] sentences = text.split("[。！？.!?]+");
        return java.util.Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public int countCharacters(String text) {
        if (text == null) {
            return 0;
        }
        return text.length();
    }

    public int countChineseCharacters(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                count++;
            }
        }
        return count;
    }
}
