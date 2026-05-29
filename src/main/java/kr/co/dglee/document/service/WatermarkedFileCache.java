package kr.co.dglee.document.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WatermarkedFileCache {

    private static final long TTL_SECONDS = 30;

    private final Path cacheDir;
    private final ConcurrentHashMap<Path, Instant> expiryMap = new ConcurrentHashMap<>();

    public WatermarkedFileCache(@Value("${app.storage.path}") String storagePath) throws IOException {
        this.cacheDir = Paths.get(storagePath, "watermark-cache");
        Files.createDirectories(cacheDir);
    }

    @PostConstruct
    public void clearStaleFiles() {
        try (var stream = Files.list(cacheDir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("워터마크 캐시 정리 실패: {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("워터마크 캐시 디렉토리 정리 실패: {}", cacheDir, e);
        }
    }

    public Optional<Path> get(String cacheKey) {
        Path file = cacheDir.resolve(cacheKey + ".pdf");
        Instant expiry = expiryMap.get(file);
        if (expiry != null
                && Instant.now().isBefore(expiry)
                && Files.exists(file)) {
            return Optional.of(file);
        }
        return Optional.empty();
    }

    public Path put(String cacheKey, byte[] data) throws IOException {
        Path file = cacheDir.resolve(cacheKey + ".pdf");
        Files.write(file, data);
        expiryMap.put(file, Instant.now().plusSeconds(TTL_SECONDS));
        return file;
    }

    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.SECONDS)
    public void evictExpired() {
        Instant now = Instant.now();
        expiryMap.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                try {
                    Files.deleteIfExists(entry.getKey());
                } catch (IOException e) {
                    log.warn("워터마크 캐시 파일 삭제 실패: {}", entry.getKey(), e);
                }
                return true;
            }
            return false;
        });
    }
}
