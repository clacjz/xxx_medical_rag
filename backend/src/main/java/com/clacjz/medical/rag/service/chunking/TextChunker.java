package com.clacjz.medical.rag.service.chunking;

import com.clacjz.medical.rag.config.ParserProperties;
import com.clacjz.medical.rag.service.parser.PageLayout;
import com.clacjz.medical.rag.service.parser.TextCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextChunker {

    private final ParserProperties parserProperties;
    private final TextCleaner textCleaner;

    public List<Chunk> chunkFixedSize(String text) {
        return chunkFixedSize(text,
                parserProperties.getChunk().getSize(),
                parserProperties.getChunk().getOverlap());
    }

    public List<Chunk> chunkFixedSize(String text, int chunkSize, int overlap) {
        log.info("[Step: chunking:chunkFixedSize] Starting, textLength={}, chunkSize={}, overlap={}",
                text != null ? text.length() : 0, chunkSize, overlap);

        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            log.warn("[Step: chunking:chunkFixedSize] Empty or null text, returning empty chunks");
            return chunks;
        }

        int textLength = text.length();
        log.info("[Step: chunking:chunkFixedSize] Text length: {}", textLength);

        if (textLength <= chunkSize) {
            chunks.add(new Chunk(0, text, 0, textLength, 0, 0));
            log.info("[Step: chunking:chunkFixedSize] Text shorter than chunkSize, created 1 chunk");
            return chunks;
        }

        int step = chunkSize - overlap;
        int startPos = 0;
        int index = 0;

        while (startPos < textLength) {
            int endPos = Math.min(startPos + chunkSize, textLength);
            String chunkText;

            if (endPos < textLength) {
                int lastPeriod = text.lastIndexOf("。", endPos);
                int lastComma = text.lastIndexOf("，", endPos);
                int lastNewline = text.lastIndexOf("\n", endPos);
                int lastSpace = text.lastIndexOf(" ", endPos);
                int lastSemicolon = text.lastIndexOf("；", endPos);

                int breakPoint = Math.max(lastPeriod, Math.max(lastNewline,
                        Math.max(lastSpace, Math.max(lastComma, lastSemicolon))));

                if (breakPoint > startPos + chunkSize / 2 && breakPoint > startPos) {
                    endPos = breakPoint + 1;
                }
            }

            chunkText = text.substring(startPos, endPos);
            String cleanedText = textCleaner.clean(chunkText);
            log.debug("[Step: chunking:chunkFixedSize] chunk {}: startPos={}, endPos={}, originalLen={}, cleanedLen={}",
                    index, startPos, endPos, chunkText.length(), cleanedText.length());

            if (!cleanedText.isBlank()) {
                chunks.add(new Chunk(index, cleanedText, startPos, endPos, 0, 0));
                index++;
            } else {
                log.warn("[Step: chunking:chunkFixedSize] Skipping blank chunk at index {}", index);
            }

            if (endPos >= textLength) {
                break;
            }

            startPos += step;
            if (startPos >= textLength) {
                break;
            }
        }

        log.info("[Step: chunking:chunkFixedSize] Completed, created {} chunks from text of {} characters",
                chunks.size(), textLength);

        return chunks;
    }

    public List<Chunk> chunkByParagraph(String text) {
        return chunkByParagraph(text, parserProperties.getChunk().getSize());
    }

    public List<Chunk> chunkByParagraph(String text, int maxChunkSize) {
        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        List<String> paragraphs = textCleaner.splitIntoParagraphs(text);
        StringBuilder currentChunk = new StringBuilder();
        int currentStart = 0;
        int index = 0;

        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    String chunkText = textCleaner.clean(currentChunk.toString());
                    int chunkLen = currentChunk.length();
                    chunks.add(new Chunk(index, chunkText, currentStart,
                            currentStart + chunkLen, 0, 0));
                    index++;
                    currentStart += chunkLen;
                    currentChunk = new StringBuilder();
                }

                if (paragraph.length() > maxChunkSize) {
                    List<Chunk> subChunks = chunkFixedSize(paragraph, maxChunkSize, 0);
                    for (int i = 0; i < subChunks.size(); i++) {
                        Chunk subChunk = subChunks.get(i);
                        chunks.add(new Chunk(index, subChunk.text(),
                                currentStart + subChunk.startPos(),
                                currentStart + subChunk.endPos(), 0, 0));
                        index++;
                    }
                    currentStart += paragraph.length();
                } else {
                    currentChunk.append(paragraph).append("\n\n");
                }
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }

        if (currentChunk.length() > 0) {
            String chunkText = textCleaner.clean(currentChunk.toString());
            chunks.add(new Chunk(index, chunkText, currentStart,
                    currentStart + currentChunk.length(), 0, 0));
        }

        log.debug("Created {} chunks from {} paragraphs", chunks.size(), paragraphs.size());

        return chunks;
    }

    /**
     * 按自然段分块（带页码布局）
     * @param text 文本内容
     * @param layouts 页面布局信息
     */
    public List<Chunk> chunkByParagraphWithLayout(String text, List<PageLayout> layouts) {
        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank() || layouts == null || layouts.isEmpty()) {
            return chunkByParagraph(text);
        }

        // 建立页码布局索引：查找每个字符位置属于哪一页
        // 简单策略：找到每个段落起始位置对应的页码
        List<String> paragraphs = textCleaner.splitIntoParagraphs(text);
        StringBuilder currentChunk = new StringBuilder();
        int currentStart = 0;
        int currentStartPage = 0;
        int index = 0;
        int maxChunkSize = parserProperties.getChunk().getSize();

        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > maxChunkSize) {
                if (currentChunk.length() > 0) {
                    String chunkText = textCleaner.clean(currentChunk.toString());
                    int chunkLen = currentChunk.length();
                    int endPage = findEndPage(currentStart + chunkLen, layouts);
                    chunks.add(new Chunk(index, chunkText, currentStart,
                            currentStart + chunkLen, currentStartPage, endPage));
                    index++;
                    currentStart += chunkLen;
                    currentChunk = new StringBuilder();
                }

                if (paragraph.length() > maxChunkSize) {
                    // 长段落需要细分，使用固定分块（但保留页码信息）
                    List<Chunk> subChunks = chunkFixedSize(paragraph, maxChunkSize, 0);
                    for (Chunk subChunk : subChunks) {
                        int startPage = findStartPage(currentStart + subChunk.startPos(), layouts);
                        chunks.add(new Chunk(index, subChunk.text(),
                                currentStart + subChunk.startPos(),
                                currentStart + subChunk.endPos(), startPage, startPage));
                        index++;
                    }
                    currentStart += paragraph.length();
                } else {
                    currentStartPage = findStartPage(currentStart, layouts);
                    currentChunk.append(paragraph).append("\n\n");
                }
            } else {
                if (currentChunk.length() == 0) {
                    currentStartPage = findStartPage(currentStart, layouts);
                }
                currentChunk.append(paragraph).append("\n\n");
            }
        }

        if (currentChunk.length() > 0) {
            String chunkText = textCleaner.clean(currentChunk.toString());
            int endPage = findEndPage(currentStart + currentChunk.length(), layouts);
            chunks.add(new Chunk(index, chunkText, currentStart,
                    currentStart + currentChunk.length(), currentStartPage, endPage));
        }

        log.debug("Created {} chunks with layout from {} paragraphs", chunks.size(), paragraphs.size());
        return chunks;
    }

    /**
     * 根据文本位置查找起始页
     */
    private int findStartPage(int position, List<PageLayout> layouts) {
        for (PageLayout layout : layouts) {
            if (position >= layout.startPos() && position < layout.endPos()) {
                return layout.pageNumber();
            }
        }
        // 找不到则返回第一页
        return layouts.isEmpty() ? 0 : layouts.get(0).pageNumber();
    }

    /**
     * 根据文本位置查找结束页
     */
    private int findEndPage(int position, List<PageLayout> layouts) {
        for (PageLayout layout : layouts) {
            if (position >= layout.startPos() && position <= layout.endPos()) {
                return layout.pageNumber();
            }
        }
        // 找不到则返回最后一页的页码
        return layouts.isEmpty() ? 0 : layouts.get(layouts.size() - 1).pageNumber();
    }

    /**
     * 按特定符号分块（如换行符、句子结束符等）
     * @param text 原始文本
     * @param delimiter 分隔符类型: "newline"(换行), "sentence"(句子), "both"(换行+句子)
     */
    public List<Chunk> chunkByDelimiter(String text, String delimiter) {
        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String splitPattern = switch (delimiter.toLowerCase()) {
            case "newline" -> "\n";
            case "sentence" -> "[。！？.!?；;]";
            case "both" -> "\n|[。！？.!?；;]";
            default -> "\n";
        };

        String[] parts = text.split(splitPattern);
        StringBuilder currentChunk = new StringBuilder();
        int currentStart = 0;
        int index = 0;
        int chunkSize = parserProperties.getChunk().getSize();

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
                continue;
            }

            if (currentChunk.length() + trimmedPart.length() <= chunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n");
                }
                currentChunk.append(trimmedPart);
            } else {
                // 保存当前chunk
                if (currentChunk.length() > 0) {
                    String chunkText = textCleaner.clean(currentChunk.toString());
                    if (!chunkText.isBlank()) {
                        chunks.add(new Chunk(index, chunkText, currentStart,
                                currentStart + currentChunk.length(), 0, 0));
                        index++;
                    }
                }
                // 开始新的chunk
                currentChunk = new StringBuilder(trimmedPart);
                // 修复：trimmedPart 已去除前后空格，可能无法在原文本中找到精确匹配
                // 改用 indexOf + 从当前累积偏移往后搜索
                int newStart = text.indexOf(trimmedPart, currentStart);
                if (newStart >= 0) {
                    currentStart = newStart;
                } else {
                    // trimmedPart 因去空格无法精确匹配，尝试按原文本中出现的下一个段落估算位置
                    currentStart = currentStart + currentChunk.length();
                    // 修正在当前段落范围内搜索原始 non-trimmed 内容
                    if (currentStart < text.length()) {
                        int searchFrom = currentStart;
                        // 向后查找到 trimmedPart 或等效的非空白片段
                        int rawIdx = text.indexOf(trimmedPart, Math.max(0, searchFrom - trimmedPart.length() - 5));
                        if (rawIdx >= 0) {
                            currentStart = rawIdx;
                        }
                    }
                }
            }
        }

        // 处理最后一个chunk
        if (currentChunk.length() > 0) {
            String chunkText = textCleaner.clean(currentChunk.toString());
            if (!chunkText.isBlank()) {
                chunks.add(new Chunk(index, chunkText, currentStart,
                        currentStart + currentChunk.length(), 0, 0));
            }
        }

        log.debug("Created {} chunks by delimiter '{}' from text of {} characters",
                chunks.size(), delimiter, text.length());

        return chunks;
    }

    /**
     * 不分块：整篇文档作为一个chunk
     */
    public List<Chunk> chunkNoSplit(String text) {
        List<Chunk> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String cleanedText = textCleaner.clean(text);
        chunks.add(new Chunk(0, cleanedText, 0, text.length(), 0, 0));

        log.debug("No chunking applied, created 1 chunk from text of {} characters", text.length());

        return chunks;
    }

    public record Chunk(
            int index,
            String text,
            int startPos,
            int endPos,
            int startPage,    // 起始页（1-based，0表示无页码信息）
            int endPage,      // 结束页
            String summary    // LLM语义分块摘要（仅semantic策略有效，其他策略为空）
    ) {
        public Chunk(int index, String text, int startPos, int endPos, int startPage, int endPage) {
            this(index, text, startPos, endPos, startPage, endPage, "");
        }
    }
}
