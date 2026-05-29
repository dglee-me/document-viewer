package kr.co.dglee.document.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String storedFilename;

    @Column(nullable = false)
    private Long fileSize;

    private Integer pageCount;

    @Builder
    private Document(String originalFilename, String storedFilename, DocumentType type, Long fileSize, Integer pageCount) {
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.type = type;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
    }

    public void updatePageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }
}
