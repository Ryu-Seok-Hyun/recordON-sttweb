package com.sttweb.sttweb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ListResponse<T> {
  private long count;
  private List<T> items;
}