package kr.co.dglee.document.dto;

import org.springframework.core.io.Resource;

public record DocumentFile(String originalFilename, Resource resource, long contentLength) {
}
