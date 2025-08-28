package com.sttweb.sttweb.service;

import com.sttweb.sttweb.crypto.CryptoUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecordingIngestService {

  @Value("${recording.auto-decrypt-legacy-encmp3:true}")
  private boolean autoDecrypt;

  @Value("${recording.keep-original:false}")
  private boolean keepOriginal;

  /** *_enc.mp3면 복호화하여 .mp3로 치환 후 최종 경로 반환. */
  public Path normalize(Path storedPath) throws Exception {
    String name = storedPath.getFileName().toString();
    if (!(autoDecrypt && name.toLowerCase().endsWith("_enc.mp3"))) return storedPath;

    Path dir = storedPath.getParent();
    Files.createDirectories(dir);
    Path tmp = Files.createTempFile(dir, "dec-", ".mp3");
    try (InputStream in = CryptoUtil.decryptingStreamLegacyEncMp3(Files.newInputStream(storedPath), name);
        OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
      in.transferTo(out);
    }
    Path dst = dir.resolve(name.replace("_enc.mp3", ".mp3"));
    Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    if (!keepOriginal) Files.deleteIfExists(storedPath);
    return dst;
  }

  /** 스캔·길이계산 용: *_enc.mp3면 복호화 스트림을, 아니면 원본 스트림을 반환. */
  // RecordingIngestService.java
  public InputStream openPossiblyDecrypted(Path path) throws IOException {
    String name = path.getFileName().toString().toLowerCase();
    if (autoDecrypt && name.endsWith("_enc.mp3")) {
      try {
        return CryptoUtil.decryptingStreamLegacyEncMp3(
            Files.newInputStream(path),
            path.getFileName().toString()
        );
      } catch (Exception e) { // CryptoUtil이 던지는 광범위한 예외 래핑
        throw new IOException("Failed to open decrypted stream for: " + path, e);
      }
    }
    return Files.newInputStream(path);
  }

}
