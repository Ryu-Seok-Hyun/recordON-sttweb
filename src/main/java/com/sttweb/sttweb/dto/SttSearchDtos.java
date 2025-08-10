// src/main/java/com/sttweb/sttweb/dto/SttSearchDtos.java
package com.sttweb.sttweb.dto;

import java.util.List;

public class SttSearchDtos {
  public record Hit(
      String id,
      String filename,
      String text,          // 원문 일부(있으면)
      List<String> highlights,
      Double score
  ) {}

  public record Page(
      List<Hit> content,
      long totalHits,
      int page,
      int size
  ) {}
}
