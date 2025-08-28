package com.sttweb.sttweb.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import org.springframework.core.io.Resource;

public class CryptoUtil {
  private static final String ALG = "AES";
  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final SecureRandom RANDOM = new SecureRandom();

  /* ---------- 공통 ---------- */
  public static SecretKey generateKey() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance(ALG);
    kg.init(256);
    return kg.generateKey();
  }

  /* ---------- .aes (앞 16바이트 IV 프리픽스) ---------- */
  public static void encryptFile(File in, File out, SecretKey key) throws Exception {
    byte[] iv = new byte[16];
    RANDOM.nextBytes(iv);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
    try (FileOutputStream fos = new FileOutputStream(out);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);
        FileInputStream fis = new FileInputStream(in)) {
      fos.write(iv); // IV prefix
      byte[] buf = new byte[8192]; int len;
      while ((len = fis.read(buf)) != -1) cos.write(buf, 0, len);
    }
  }

  public static void decryptFile(File in, File out, SecretKey key) throws Exception {
    try (FileInputStream fis = new FileInputStream(in)) {
      byte[] iv = fis.readNBytes(16);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
      try (CipherInputStream cis = new CipherInputStream(fis, cipher);
          FileOutputStream fos = new FileOutputStream(out)) {
        byte[] buf = new byte[8192]; int len;
        while ((len = cis.read(buf)) != -1) fos.write(buf, 0, len);
      }
    }
  }

  public static InputStream decryptingStream(InputStream encrypted, SecretKey key) throws Exception {
    byte[] iv = encrypted.readNBytes(16);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
    return new CipherInputStream(encrypted, cipher);
  }

  /* ---------- legacy *_enc.mp3 (PHP AES-256-CBC) ---------- */
  private static SecretKeySpec legacyKey(String fileName) {
    // 파일명 앞 21바이트 → 32바이트(0패딩)
    byte[] src = fileName.getBytes(StandardCharsets.ISO_8859_1);
    byte[] key = new byte[32];
    int copy = Math.min(21, src.length);
    System.arraycopy(src, 0, key, 0, copy);
    return new SecretKeySpec(key, ALG);
  }

  private static IvParameterSpec legacyIv(String fileName) {
    // 파일명 앞 16바이트(0패딩)
    byte[] src = fileName.getBytes(StandardCharsets.ISO_8859_1);
    byte[] iv  = new byte[16];
    int copy = Math.min(16, src.length);
    System.arraycopy(src, 0, iv, 0, copy);
    return new IvParameterSpec(iv);
  }

  /** *_enc.mp3: 파일명 기반 key/iv 로 복호화 스트림 */
  public static InputStream decryptingStreamLegacyEncMp3(InputStream encrypted, String fileName) throws Exception {
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, legacyKey(fileName), legacyIv(fileName));
    return new CipherInputStream(encrypted, cipher);
  }

  /** *_enc.mp3: 임시 mp3 파일로 완전 복호화 (Range/길이 계산용) */
  public static File decryptToTempLegacyEncMp3(Resource encRes, String fileName) throws Exception {
    File tmp = java.nio.file.Files.createTempFile("dec-encmp3-", ".mp3").toFile();
    try (InputStream in = decryptingStreamLegacyEncMp3(encRes.getInputStream(), fileName);
        OutputStream out = new FileOutputStream(tmp)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }
    return tmp;
  }
}
