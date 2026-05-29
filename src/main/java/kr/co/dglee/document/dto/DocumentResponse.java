package kr.co.dglee.document.dto;

import kr.co.dglee.document.domain.Document;
import kr.co.dglee.document.domain.DocumentType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentResponse {

    private Long id;
    private String originalFilename;
    private DocumentType documentType;
    private Long fileSize;
    private Integer pageCount;
    private LocalDateTime createdAt;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .originalFilename(document.getOriginalFilename())
                .documentType(document.getType())
                .fileSize(document.getFileSize())
                .pageCount(document.getPageCount())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
