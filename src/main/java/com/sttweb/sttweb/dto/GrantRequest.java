package com.sttweb.sttweb.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public class GrantRequest {
  @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
  private List<GrantDto> grants;

  // ① normal setter-based deserialization
  public GrantRequest() {}

  // ② JSON array가 root로 바로 들어올 때 이 생성자 사용
  @JsonCreator
  public GrantRequest(List<GrantDto> grants) {
    this.grants = grants;
  }

  public List<GrantDto> getGrants() { return grants; }
  public void setGrants(List<GrantDto> grants) { this.grants = grants; }
}
