package kr.co.dglee.document.storage;

import kr.co.dglee.document.domain.DocumentType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentTypeDetector {

    private final Tika tika = new Tika();

    public DocumentType detect(MultipartFile file) {
        String mimeType;
        try (InputStream inputStream = file.getInputStream()) {
            mimeType = tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다.", e);
        }

        DocumentType type = DocumentType.fromMimeType(mimeType);
        if (type == null) {
            String supported = Arrays.stream(DocumentType.values())
                    .map(DocumentType::getMimeType)
                    .collect(Collectors.joining(", "));
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "지원하지 않는 파일 형식입니다. 지원 형식: " + supported
            );
        }
        return type;
    }
}
