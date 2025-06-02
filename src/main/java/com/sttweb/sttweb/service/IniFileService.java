// src/main/java/com/sttweb/sttweb/service/IniFileService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.IniNameExtDto;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IniFileService {

  private final TrecordTelListRepository repository;

  // Windows 환경에서 순차 탐색할 드라이브 목록
  private static final String[] DRIVES = { "C:", "D:", "E:" };
  private static final String SUB_PATH = "\\RecOnData\\RecOnLineInfo.ini";

  /**
   * 1) 드라이브(C:,D:,E:)를 순차 탐색해서
   *    RecOnLineInfo.ini 파일을 찾으면 File을 반환. 없으면 null 반환
   */
  public File findIniFile() {
    for (String drive : DRIVES) {
      String fullPath = drive + SUB_PATH;
      File f = new File(fullPath);
      if (f.exists() && f.isFile() && f.canRead()) {
        return f;
      }
    }
    return null;
  }

  /**
   * 2) INI 파일을 ANSI(MS949)로 읽어서 "[RECORD_TELLIST]" 섹션 이후 줄을 파싱
   *    callNum, userName만 RecordTelDto에 담아 반환
   *
   * @return 파싱된 RecordTelDto 목록 (INI 파일이 없으면 빈 리스트)
   * @throws IOException 파일 읽기/파싱 중 예외 발생 시
   */
  public List<IniNameExtDto> parseIniNameExt() throws IOException {
    List<IniNameExtDto> result = new ArrayList<>();
    File iniFile = findIniFile();
    if (iniFile == null) {
      // 파일이 없으면 빈 리스트 반환
      return result;
    }

    // MS949 인코딩으로 읽기 (한글 깨짐 방지)
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(iniFile), Charset.forName("MS949"))
    )) {
      String line;
      boolean inSection = false;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        // "[RECORD_TELLIST]" 섹션 나오기 전에는 무시
        if (!inSection) {
          if (line.startsWith("[RECORD_TELLIST]")) {
            inSection = true;
          }
          continue;
        }

        // 섹션 내부에서는 "COUNT=" 줄 스킵
        if (line.startsWith("COUNT")) {
          continue;
        }

        // "index=..." 형태인 줄만 파싱
        if (line.contains("=")) {
          String[] parts = line.split("=", 2);
          String idx = parts[0].trim();
          String data = parts[1].trim();

          if (isNumeric(idx)) {
            // data 예: "202;N;202;서인석;3;X;N;13688;"
            String[] tokens = data.split(";");
            if (tokens.length >= 4) {
              String callNum = tokens[0];
              String userName = tokens[3];
              // id, critime은 파싱 단계에는 알 수 없으므로 null
              result.add(IniNameExtDto.builder()
                  .callNum(callNum)
                  .userName(userName)
                  .build()
              );
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * 3) 파싱된 RecordTelDto 목록을 DB에 저장 (중복 검사 → INSERT)
   *    → 기존 DB에 없는 callNum만 저장
   *    → 저장된 엔티티를 RecordTelDto로 변환하여 리스트로 반환
   *
   * @return 새로 삽입된 레코드를 담은 List<RecordTelDto>
   * @throws IOException 파싱 중 예외 발생 시
   */
  @Transactional
  public List<IniNameExtDto> syncFromIni() throws IOException {
    List<IniNameExtDto> parsedList = parseIniNameExt();
    List<IniNameExtDto> insertedDtos = new ArrayList<>();

    for (IniNameExtDto dto : parsedList) {
      String callNum = dto.getCallNum();
      String userName = dto.getUserName();

      Optional<TrecordTelListEntity> existing = repository.findByCallNum(callNum);
      if (existing.isEmpty()) {
        // 새 엔티티 생성
        TrecordTelListEntity entity = TrecordTelListEntity.builder()
            .callNum(callNum)
            .userName(userName)
            // critime은 @CreationTimestamp 덕분에 Hibernate가 자동으로 채워줌
            .build();
        TrecordTelListEntity saved = repository.save(entity);

        // 저장된 엔티티를 DTO로 변환하여 리스트에 추가
        IniNameExtDto savedDto = IniNameExtDto.builder()
            .id(saved.getId())
            .callNum(saved.getCallNum())
            .userName(saved.getUserName())
            .critime(saved.getCritime())
            .build();
        insertedDtos.add(savedDto);
      }
    }
    return insertedDtos;
  }

  /**
   * 4) trecord_tel_list 테이블의 모든 행을 조회하여
   *    RecordTelDto(id, callNum, userName, critime) 리스트 반환
   */
  @Transactional(readOnly = true)
  public List<IniNameExtDto> getAll() {
    return repository.findAll()
        .stream()
        .map(this::entityToDto)
        .toList();
  }

  /**
   * 5) callNum(내선번호)로 단일 조회 → RecordTelDto 반환 (없으면 null)
   */
  @Transactional(readOnly = true)
  public IniNameExtDto getByCallNum(String callNum) {
    return repository.findByCallNum(callNum)
        .map(this::entityToDto)
        .orElse(null);
  }

  /** 엔티티 → DTO 변환 헬퍼 */
  private IniNameExtDto entityToDto(TrecordTelListEntity e) {
    return IniNameExtDto.builder()
        .id(e.getId())
        .callNum(e.getCallNum())
        .userName(e.getUserName())
        .critime(e.getCritime())
        .build();
  }

  /** 문자열이 숫자만으로 이루어졌는지 확인하는 헬퍼 */
  private boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    for (char c : s.toCharArray()) {
      if (!Character.isDigit(c)) return false;
    }
    return true;
  }
}