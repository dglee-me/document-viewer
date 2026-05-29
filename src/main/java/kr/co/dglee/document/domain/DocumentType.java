package kr.co.dglee.document.domain;

public enum DocumentType {
    PDF("application/pdf", ".pdf");

    private final String mimeType;
    private final String extension;

    DocumentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String generateFilename() {
        return java.util.UUID.randomUUID() + extension;
    }

    public static DocumentType fromMimeType(String mimeType) {
        for (DocumentType type : values()) {
            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        return null;
    }
}
