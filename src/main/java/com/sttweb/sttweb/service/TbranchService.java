package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.TbranchDto;
import java.util.List;

public interface TbranchService {
  List<TbranchDto> findAll();
  TbranchDto findById(Integer branchSeq);
  TbranchDto createBranch(TbranchDto dto);
  TbranchDto update(Integer branchSeq, TbranchDto dto);
  void changeStatus(Integer branchSeq, boolean active);
  void deleteBranch(Integer branchSeq);
}