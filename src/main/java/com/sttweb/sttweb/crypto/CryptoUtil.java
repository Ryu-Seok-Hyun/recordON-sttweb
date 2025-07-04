package com.sttweb.sttweb.crypto;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;

public class CryptoUtil {

  private static final String ALG = "AES";
  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final SecureRandom RANDOM = new SecureRandom();

  /** 테스트용 키 생성 */
  public static SecretKey generateKey() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance(ALG);
    kg.init(256);
    return kg.generateKey();
  }

  /** 파일 암호화: IV(16바이트) + ciphertext */
  public static void encryptFile(File in, File out, SecretKey key) throws Exception {
    byte[] iv = new byte[16];
    RANDOM.nextBytes(iv);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

    try (FileOutputStream fos = new FileOutputStream(out);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);
        FileInputStream fis = new FileInputStream(in)) {

      fos.write(iv);  // 1) IV 기록
      byte[] buf = new byte[8192];
      int len;
      while ((len = fis.read(buf)) != -1) {
        cos.write(buf, 0, len);
      }
    }
  }

  /** 파일 복호화 */
  public static void decryptFile(File in, File out, SecretKey key) throws Exception {
    try (FileInputStream fis = new FileInputStream(in)) {
      byte[] iv = fis.readNBytes(16);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

      try (CipherInputStream cis = new CipherInputStream(fis, cipher);
          FileOutputStream fos = new FileOutputStream(out)) {

        byte[] buf = new byte[8192];
        int len;
        while ((len = cis.read(buf)) != -1) {
          fos.write(buf, 0, len);
        }
      }
    }
  }

  /** 스트리밍 복호화 */
  public static InputStream decryptingStream(InputStream encryptedStream, SecretKey key) throws Exception {
    byte[] iv = encryptedStream.readNBytes(16);
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
    return new CipherInputStream(encryptedStream, cipher);
  }
}
