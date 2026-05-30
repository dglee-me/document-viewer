package kr.co.dglee.document.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PdfWatermarkService {

    private static final float FONT_SIZE = 28f;
    private static final float ALPHA = 0.15f;
    private static final float TILE_GAP_X = 140f;
    private static final float TILE_GAP_Y = 110f;
    private static final float ANGLE_RAD = (float) Math.toRadians(35);
    private static final String DEFAULT_MAC_UNICODE_FONT = "/Library/Fonts/Arial Unicode.ttf";

    @Value("${app.watermark.font-path:}")
    private String watermarkFontPath;

    public byte[] apply(byte[] pdfBytes, String text) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFont font = loadWatermarkFont(doc);
            float textWidth = font.getStringWidth(text) / 1000f * FONT_SIZE;

            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(ALPHA);
            gs.setAlphaSourceFlag(true);

            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                float w = box.getWidth();
                float h = box.getHeight();
                float cx = w / 2f;
                float cy = h / 2f;
                float diagonal = (float) Math.sqrt(w * w + h * h);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
                    cs.saveGraphicsState();
                    cs.setGraphicsStateParameters(gs);
                    // 좌표계를 페이지 중앙 기준으로 45도 회전
                    cs.transform(Matrix.getRotateInstance(ANGLE_RAD, cx, cy));
                    cs.setNonStrokingColor(0.65f, 0.65f, 0.65f);

                    // 회전된 좌표계에서 수평 격자로 배치 → 실제로는 대각선 타일링
                    for (float y = -diagonal; y <= diagonal; y += TILE_GAP_Y) {
                        for (float x = -diagonal; x <= diagonal; x += TILE_GAP_X + textWidth) {
                            drawText(cs, font, text, x, y);
                        }
                    }
                    cs.restoreGraphicsState();
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private PDFont loadWatermarkFont(PDDocument doc) throws IOException {
        Path configured = watermarkFontPath == null || watermarkFontPath.isBlank()
                ? null
                : Path.of(watermarkFontPath);
        if (configured != null && Files.isRegularFile(configured)) {
            return PDType0Font.load(doc, configured.toFile());
        }

        File defaultFont = new File(DEFAULT_MAC_UNICODE_FONT);
        if (defaultFont.isFile()) {
            return PDType0Font.load(doc, defaultFont);
        }

        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private void drawText(PDPageContentStream cs, PDFont font, String text, float x, float y) throws IOException {
        cs.beginMarkedContent(COSName.ARTIFACT);
        cs.beginText();
        cs.setFont(font, FONT_SIZE);
        cs.setTextMatrix(Matrix.getTranslateInstance(x, y));
        cs.showText(text);
        cs.endText();
        cs.endMarkedContent();
    }
}
