package com.sttweb.sttweb.repository;

import com.sttweb.sttweb.entity.Tline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TlineRepository extends JpaRepository<Tline, Integer> {

  Optional<Tline> findByCallNum(String callNum);

  List<Tline> findByCallNumIn(List<String> callNums);
}