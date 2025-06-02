// src/main/java/com/sttweb/sttweb/service/RecordTelService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.IniNameExtDto;
import com.sttweb.sttweb.entity.TrecordTelListEntity;
import com.sttweb.sttweb.repository.TrecordTelListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordTelService {

  private final TrecordTelListRepository repository;

  // 순차 탐색할 드라이브 목록 (Windows)
  private static final String[] DRIVES = { "C:", "D:", "E:" };
  private static final String SUB_PATH = "\\RecOnData\\RecOnLineInfo.ini";

  /**
   * 1) C:, D:, E: 드라이브 순으로
   *    RecOnLineInfo.ini 파일을 찾아 반환 (없으면 null)
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
   * 2) INI 파일에서 "[RECORD_TELLIST]" 섹션 이후의 줄을 읽어서
   *    각 항목(index=... 형태)에서 callNum, userName만 추출하여
   *    (임시로) DTO 리스트를 만듭니다.
   *
   *    - id, critime은 이 단계에선 알 수 없으므로 null로 둡니다.
   *    - 인코딩이 ANSI(MS949)인 경우에도 한글이 깨지지 않도록 Charset.forName("MS949")로 읽습니다.
   *
   * @return 파싱된 DTO 목록 (INI 파일이 없으면 빈 리스트)
   * @throws IOException 파일 읽기/파싱 중 예외 발생 시
   */
  public List<IniNameExtDto> parseIniNameExt() throws IOException {
    List<IniNameExtDto> result = new ArrayList<>();
    File iniFile = findIniFile();
    if (iniFile == null) {
      // 파일이 없으면 빈 리스트 반환
      return result;
    }

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(iniFile), Charset.forName("MS949"))
    )) {
      String line;
      boolean inSection = false;

      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;

        // "[RECORD_TELLIST]" 섹션을 만날 때까지 스킵
        if (!inSection) {
          if (line.startsWith("[RECORD_TELLIST]")) {
            inSection = true;
          }
          continue;
        }

        // 섹션 내부: "COUNT=" 줄 스킵
        if (line.startsWith("COUNT")) {
          continue;
        }

        // "index=..." 형태 줄만 파싱
        if (line.contains("=")) {
          String[] parts = line.split("=", 2);
          String idx = parts[0].trim();
          String data = parts[1].trim();

          if (isNumeric(idx)) {
            // ex: "202;N;202;서인석;3;X;N;13688;"
            String[] tokens = data.split(";");
            if (tokens.length >= 4) {
              String callNum = tokens[0];
              String userName = tokens[3];
              // id, critime은 파싱 단계에 알 수 없으므로 null
              result.add(IniNameExtDto.builder()
                  .id(null)
                  .callNum(callNum)
                  .userName(userName)
                  .critime(null)
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
   * 3) parseIniNameExt()로 파싱한 결과를 순회하면서,
   *    DB(trecord_tel_list)에 “중복 검사(callNum)” → “신규 INSERT” 를 수행합니다.
   *    이때, 엔티티를 저장하면 자동으로 critime이 채워집니다.
   *
   * @return 실제 INSERT된 레코드의 DTO 리스트
   * @throws IOException 파일 읽기/파싱 중 예외 발생 시
   */
  @Transactional
  public List<IniNameExtDto> syncFromIni() throws IOException {
    List<IniNameExtDto> parsedList = parseIniNameExt();
    List<IniNameExtDto> insertedDtos = new ArrayList<>();

    for (IniNameExtDto dto : parsedList) {
      String callNum = dto.getCallNum();
      String userName = dto.getUserName();

      // 이미중복 검사
      Optional<TrecordTelListEntity> existing = repository.findByCallNum(callNum);
      if (existing.isEmpty()) {
        TrecordTelListEntity entity = TrecordTelListEntity.builder()
            .callNum(callNum)
            .userName(userName)
            .build();
        TrecordTelListEntity saved = repository.save(entity);

        // 방금 저장된 엔티티의 id, critime까지 포함하여 DTO 생성
        insertedDtos.add(IniNameExtDto.builder()
            .id(saved.getId())
            .callNum(saved.getCallNum())
            .userName(saved.getUserName())
            .critime(saved.getCritime())
            .build()
        );
      }
    }
    return insertedDtos;
  }

  /**
   * 4) trecord_tel_list 테이블의 모든 행을 조회한 뒤
   *    각각의 엔티티를 DTO로 변환하여 List<DTO>로 반환합니다.
   */
  @Transactional(readOnly = true)
  public List<IniNameExtDto> getAll() {
    return repository.findAll().stream()
        .map(this::entityToDto)
        .collect(Collectors.toList());
  }

  /**
   * 5) callNum 기준으로 단일 조회 후 엔티티 → DTO 변환
   *    없으면 null 반환
   */
  @Transactional(readOnly = true)
  public IniNameExtDto getByCallNum(String callNum) {
    return repository.findByCallNum(callNum)
        .map(this::entityToDto)
        .orElse(null);
  }

  // 엔티티 → DTO 헬퍼
  private IniNameExtDto entityToDto(TrecordTelListEntity e) {
    return IniNameExtDto.builder()
        .id(e.getId())
        .callNum(e.getCallNum())
        .userName(e.getUserName())
        .critime(e.getCritime())
        .build();
  }

  // 숫자 여부 확인 헬퍼
  private boolean isNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    for (char c : s.toCharArray()) {
      if (!Character.isDigit(c)) return false;
    }
    return true;
  }
}
