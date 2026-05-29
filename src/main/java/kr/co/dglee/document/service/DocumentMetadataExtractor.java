package kr.co.dglee.document.service;

import kr.co.dglee.document.domain.DocumentType;
import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DocumentMetadataExtractor {

    public Integer extractPageCount(DocumentType documentType, Path file) {
        if (documentType != DocumentType.PDF) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.warn("PDF 페이지 수를 추출할 수 없습니다. file={}", file, e);
            return null;
        }
    }
}
