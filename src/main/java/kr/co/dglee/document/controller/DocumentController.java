package kr.co.dglee.document.controller;

import kr.co.dglee.document.dto.DocumentFile;
import kr.co.dglee.document.dto.DocumentResponse;
import kr.co.dglee.document.service.DocumentService;
import java.net.URLEncoder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file) {
        return documentService.upload(file);
    }

    @GetMapping
    public List<DocumentResponse> findAll() {
        return documentService.findAll();
    }

    @GetMapping("/{id}")
    public DocumentResponse findById(@PathVariable Long id) {
        return documentService.findById(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<ResourceRegion> serveFile(
            @PathVariable Long id,
            @RequestHeader HttpHeaders headers) {
        DocumentFile documentFile = documentService.loadFile(id);
        return buildRangeResponse(documentFile, headers.getRange());
    }

    private ResponseEntity<ResourceRegion> buildRangeResponse(DocumentFile documentFile, List<HttpRange> ranges) {
        long contentLength = documentFile.contentLength();
        ResourceRegion region;
        HttpStatus status;

        if (ranges.isEmpty()) {
            region = new ResourceRegion(documentFile.resource(), 0, contentLength);
            status = HttpStatus.OK;
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            region = new ResourceRegion(documentFile.resource(), start, end - start + 1);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        String encodedFilename = URLEncoder.encode(documentFile.originalFilename(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                .body(region);
    }
}
