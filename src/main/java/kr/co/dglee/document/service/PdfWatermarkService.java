package kr.co.dglee.document.service;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PdfWatermarkService {

    private static final float FONT_SIZE = 28f;
    private static final float ALPHA = 0.15f;
    private static final float TILE_GAP_X = 140f;
    private static final float TILE_GAP_Y = 110f;
    private static final float ANGLE_RAD = (float) Math.toRadians(35);
    private static final float FONT_SCALE = FONT_SIZE / 1000f;

    public byte[] apply(byte[] pdfBytes, String text) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
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
                            drawOutlinedText(cs, font, text, x, y);
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

    private void drawOutlinedText(PDPageContentStream cs, PDType1Font font, String text, float x, float y)
            throws IOException {
        float cursor = 0f;
        ByteArrayInputStream codes = new ByteArrayInputStream(font.encode(text));

        while (codes.available() > 0) {
            int code = font.readCode(codes);
            GeneralPath glyph = font.getPath(code);
            AffineTransform transform = new AffineTransform();
            transform.translate(x + cursor, y);
            transform.scale(FONT_SCALE, FONT_SCALE);
            appendPath(cs, transform.createTransformedShape(glyph));
            cursor += font.getWidth(code) * FONT_SCALE;
        }
        cs.fill();
    }

    private void appendPath(PDPageContentStream cs, Shape shape) throws IOException {
        float[] coords = new float[6];
        PathIterator iterator = shape.getPathIterator(null);

        while (!iterator.isDone()) {
            switch (iterator.currentSegment(coords)) {
                case PathIterator.SEG_MOVETO -> cs.moveTo(coords[0], coords[1]);
                case PathIterator.SEG_LINETO -> cs.lineTo(coords[0], coords[1]);
                case PathIterator.SEG_CUBICTO -> cs.curveTo(
                        coords[0], coords[1],
                        coords[2], coords[3],
                        coords[4], coords[5]);
                case PathIterator.SEG_CLOSE -> cs.closePath();
                default -> {
                    // Type 1 glyphs are cubic paths; quadratic segments are not expected.
                }
            }
            iterator.next();
        }
    }
}
