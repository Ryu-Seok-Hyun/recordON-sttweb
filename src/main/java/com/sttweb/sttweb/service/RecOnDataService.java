package com.sttweb.sttweb.service;


import com.sttweb.sttweb.dto.RecordStatusDto;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 제이슨 생기는지 여부
@Service
public class RecOnDataService {

  @Value("${recodata.drives}")
  private List<String> drives;

  @Value("${recodata.base-folder}")
  private String baseFolder;

  /**
   * 주어진 날짜(dateDir) 폴더를 스캔해서 .wav 파일마다 JSON 생성 여부를 리턴
   * @param dateDir "20250619"처럼 YYYYMMDD 형식
   */
  public List<RecordStatusDto> listRecordStatus(String dateDir) {
    List<RecordStatusDto> result = new ArrayList<>();

    for (String drive : drives) {
      Path dir = Paths.get(drive, baseFolder, dateDir);
      if (!Files.isDirectory(dir)) continue;

      try (DirectoryStream<Path> stream =
          Files.newDirectoryStream(dir, "*.wav")) {
        for (Path wavPath : stream) {
          String wavName = wavPath.getFileName().toString();
          String baseName = wavName.substring(0, wavName.lastIndexOf('.'));
          Path jsonPath = dir.resolve(baseName + ".json");
          boolean exists = Files.exists(jsonPath);
          result.add(new RecordStatusDto(wavName, exists));
        }
      } catch (IOException e) {
        // 필요에 따라 로깅
        e.printStackTrace();
      }
    }

    return result;
  }

  /**
   * 특정 파일 하나만 체크하고 싶을 때
   */
  public boolean isJsonGenerated(String dateDir, String wavFileName) {
    String baseName = wavFileName.replaceFirst("\\.wav$", "");
    for (String drive : drives) {
      Path jsonPath = Paths.get(drive, baseFolder, dateDir, baseName + ".json");
      if (Files.exists(jsonPath)) return true;
    }
    return false;
  }

  /**
   * 내선별 STT 활성화 여부를 INI(혹은 텍스트파일)에서 파싱
   * (예시로 "C:/RecOnData/RecOnLineInfo.ini" 파일에서 읽어옴)
   */
  public Map<String, Integer> parseSttStatusFromIni() {
    Map<String, Integer> extSttMap = new HashMap<>();
    for (String drive : drives) {
      File iniFile = new File(drive + File.separator + baseFolder + File.separator + "RecOnLineInfo.ini");
      if (!iniFile.exists()) continue;

      try (BufferedReader reader = new BufferedReader(new FileReader(iniFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          int eqIdx = line.indexOf('=');
          if (eqIdx < 0) continue;
          String[] fields = line.substring(eqIdx + 1).split(";");
          if (fields.length < 2) continue;
          String ext = fields[0].replaceAll("^0+", "");
          int enabled = 0;
          // 마지막부터 역순으로 숫자를 찾음 (마지막에 ; 공백 있을 수 있어서)
          for (int i = fields.length - 1; i >= 0; i--) {
            String val = fields[i].trim();
            if (!val.isEmpty() && val.matches("\\d+")) {
              enabled = Integer.parseInt(val);
              break;
            }
          }
          extSttMap.put(ext, enabled);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return extSttMap;
  }


}