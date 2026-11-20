package com.group4.kaoyan.modules.knowledge.parse;

import com.group4.kaoyan.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 按扩展名解析文档为纯文本。扫描版 PDF 需要 OCR，此处不处理。
 */
@Slf4j
@Component
public class DocumentParserFactory {

    public String parse(InputStream in, String ext) {
        String e = ext == null ? "" : ext.toLowerCase().replace(".", "");
        try {
            return switch (e) {
                case "pdf" -> parsePdf(in);
                case "docx" -> parseDocx(in);
                case "txt", "md", "markdown", "csv" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
                default -> throw new BizException("暂不支持的文件类型：" + ext);
            };
        } catch (BizException e2) {
            throw e2;
        } catch (Exception ex) {
            throw new BizException(500, "文档解析失败：" + ex.getMessage());
        }
    }

    private String parsePdf(InputStream in) throws Exception {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String parseDocx(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (!p.getText().isBlank()) {
                    sb.append(p.getText()).append('\n');
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText()).append('\t');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }
}
