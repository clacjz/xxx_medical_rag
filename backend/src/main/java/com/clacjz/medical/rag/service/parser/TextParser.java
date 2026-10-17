package com.clacjz.medical.rag.service.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextParser implements DocumentParser {

    private final TextCleaner textCleaner;

    @Override
    public String parse(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            String rawText = String.join("\n", lines);
            return textCleaner.clean(rawText);

        } catch (Exception e) {
            log.error("Error parsing text file", e);
            throw new RuntimeException("Failed to parse text file: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{"txt", "text"};
    }
}
