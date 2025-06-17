package com.sttweb.sttweb.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.*;

public class MultiReadHttpServletRequest extends HttpServletRequestWrapper {

  private byte[] body;

  public MultiReadHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    // 요청 본문을 모두 읽어서 바이트 배열로 보관
    InputStream is = request.getInputStream();
    this.body = StreamUtils.copyToByteArray(is);
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream bais = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override public boolean isFinished() { return bais.available() == 0; }
      @Override public boolean isReady()    { return true; }
      @Override public void setReadListener(ReadListener rl) {}
      @Override public int read() throws IOException { return bais.read(); }
    };
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(this.getInputStream(), getCharacterEncoding()));
  }
}
