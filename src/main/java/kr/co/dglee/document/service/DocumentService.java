package kr.co.dglee.document.service;

import kr.co.dglee.document.domain.Document;
import kr.co.dglee.document.domain.DocumentType;
import kr.co.dglee.document.dto.DocumentFile;
import kr.co.dglee.document.dto.DocumentResponse;
import kr.co.dglee.document.repository.DocumentRepository;
import kr.co.dglee.document.storage.DocumentTypeDetector;
import kr.co.dglee.document.storage.FileStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final FileStorage fileStorage;
    private final DocumentTypeDetector documentTypeDetector;
    private final DocumentMetadataExtractor documentMetadataExtractor;
    private final DocumentRepository documentRepository;
    private final PdfWatermarkService pdfWatermarkService;
    private final WatermarkedFileCache watermarkedFileCache;

    @Value("${app.watermark.text:CONFIDENTIAL}")
    private String watermarkText;

    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 비어있습니다.");
        }

        DocumentType documentType = documentTypeDetector.detect(file);
        String storedFilename = documentType.generateFilename();

        // 문서 DB 저장
        Document saved = documentRepository.save(Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .type(documentType)
                .fileSize(file.getSize())
                .build());

        Path storedPath = fileStorage.save(file, storedFilename);
        try {
            // PDF 문서의 전체 페이지 갯수 추출 및 저장
            Integer pageCount = documentMetadataExtractor.extractPageCount(documentType, storedPath);
            saved.updatePageCount(pageCount);

            return DocumentResponse.from(saved);
        } catch (RuntimeException e) {
            fileStorage.deleteQuietly(storedPath);
            throw e;
        }
    }

    public List<DocumentResponse> findAll() {
        return documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse findById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
        return DocumentResponse.from(document);
    }

    public DocumentFile loadFile(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        try {
            if (document.getType() != DocumentType.PDF) {
                Resource resource = fileStorage.load(document.getStoredFilename());
                return new DocumentFile(document.getOriginalFilename(), resource, resource.contentLength());
            }

            String cacheKey = id + "_" + Integer.toHexString(watermarkText.hashCode());
            Optional<Path> cached = watermarkedFileCache.get(cacheKey);
            if (cached.isPresent()) {
                Resource resource = new UrlResource(cached.get().toUri());
                return new DocumentFile(document.getOriginalFilename(), resource, resource.contentLength());
            }

            byte[] watermarked = pdfWatermarkService.apply(fileStorage.load(document.getStoredFilename()).getContentAsByteArray(), watermarkText);

            Path cachedFile = watermarkedFileCache.put(cacheKey, watermarked);
            Resource resource = new UrlResource(cachedFile.toUri());

            return new DocumentFile(document.getOriginalFilename(), resource, resource.contentLength());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.", e);
        }
    }
}
