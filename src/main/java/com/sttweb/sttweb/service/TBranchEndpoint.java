package com.sttweb.sttweb.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TBranchEndpoint {
  private final String branchName;
  private final String ip;
  private final int    port;
  private final String serviceName;
}
