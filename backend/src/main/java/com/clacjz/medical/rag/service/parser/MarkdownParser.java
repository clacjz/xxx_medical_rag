package com.clacjz.medical.rag.service.parser;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownParser implements DocumentParser {

    private final TextCleaner textCleaner;
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]+`");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]+\\)");
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);

    @Override
    public String parse(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder rawContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                rawContent.append(line).append("\n");
            }

            String rawText = rawContent.toString();
            String cleanedText = cleanMarkdown(rawText);

            return textCleaner.clean(cleanedText);

        } catch (Exception e) {
            log.error("Error parsing markdown file", e);
            throw new RuntimeException("Failed to parse markdown file: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String markdown) {
        String text = markdown;

        text = CODE_BLOCK_PATTERN.matcher(text).replaceAll(" ");
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll(" ");
        text = MARKDOWN_LINK_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_IMAGE_PATTERN.matcher(text).replaceAll("$1");
        text = MARKDOWN_HEADING_PATTERN.matcher(text).replaceAll("");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");

        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");

        text = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE).matcher(text).replaceAll("\n- ");
        text = Pattern.compile("^\\s*\\d+\\.\\s+", Pattern.MULTILINE).matcher(text).replaceAll("\n1. ");

        text = text.replaceAll("---", "\n");
        text = text.replaceAll("\\|", " ");

        return text;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"md", "markdown"};
    }
}
