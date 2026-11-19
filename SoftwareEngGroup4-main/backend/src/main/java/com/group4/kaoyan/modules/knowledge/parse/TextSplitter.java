package com.group4.kaoyan.modules.knowledge.parse;

import com.group4.kaoyan.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按字符窗口切片，优先在换行处截断，块之间保留 overlap 字符的重叠。
 */
@Component
@RequiredArgsConstructor
public class TextSplitter {

    private final AiProperties props;

    public List<String> split(String raw) {
        List<String> chunks = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return chunks;
        }
        String text = raw.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
        int size = Math.max(100, props.getRag().getChunkSize());
        int overlap = Math.max(0, Math.min(props.getRag().getChunkOverlap(), size - 1));

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int cut = text.lastIndexOf('\n', end);
                if (cut > start + size / 2) {
                    end = cut;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }
}
