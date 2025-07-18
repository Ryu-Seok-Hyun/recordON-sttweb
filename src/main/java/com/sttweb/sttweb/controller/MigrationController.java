package com.sttweb.sttweb.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * SQLite DB 파일을 업로드받아 MySQL DB로 데이터와 구조를 마이그레이션하는 컨트롤러
 * 파일 업로드는 POST /migrate 엔드포인트에서 처리
 */
@RestController
@RequestMapping("/api")
public class MigrationController {

  // application.properties에서 MySQL 접속 정보를 주입받음
  @Value("${migration.mysql.url}")
  private String MYSQL_URL;
  @Value("${migration.mysql.user}")
  private String MYSQL_USER;
  @Value("${migration.mysql.pass}")
  private String MYSQL_PASS;

  // SQLite 타입 → MySQL 타입 매핑 표
  private static final Map<String, String> TYPE_MAP = new HashMap<>();

  static {
    TYPE_MAP.put("INTEGER", "INT(11)");
    TYPE_MAP.put("REAL", "DOUBLE");
    TYPE_MAP.put("TEXT", "TEXT");
    TYPE_MAP.put("BLOB", "BLOB");
  }

  /**
   * [POST] /migrate
   */
  @PostMapping(value = "/migrate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public String migrateSqliteToMysql(@RequestParam("dbfile") MultipartFile dbfile) throws Exception {
    File tempFile = File.createTempFile("uploaded_", ".db");
    dbfile.transferTo(tempFile);

    String resultMsg;
    try {
      resultMsg = doMigrate(tempFile.getAbsolutePath(), dbfile.getOriginalFilename());
    } finally {
      tempFile.delete();
    }
    return resultMsg;
  }

  /**
   * SQLite DB에서 테이블/컬럼/데이터를 읽어 MySQL로 복사
   */
  private String doMigrate(String sqliteDbPath, String displayName) {
    StringBuilder log = new StringBuilder();
    log.append("마이그레이션 시작! (파일: ").append(displayName).append(")\n");

    // 1. JDBC 드라이버 확인
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      log.append("[마이그레이션 오류]\nSQLite JDBC 드라이버를 찾을 수 없습니다.\n");
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      log.append(sw.toString());
      return log.toString();
    }

    // 2. 실제 DB 연결 및 마이그레이션
    try (
        Connection src = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath);
        Connection dst = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASS)
    ) {
      src.setAutoCommit(false);
      dst.setAutoCommit(true);

      List<String> tables = getSqliteTables(src);
      log.append("총 ").append(tables.size()).append("개 테이블 발견: ").append(tables).append("\n");

      for (String tbl : tables) {
        log.append("\n>>> 테이블 처리: ").append(tbl).append("\n");
        if (!mysqlTableExists(dst, tbl)) {
          String ddl = getSqliteDDL(src, tbl);
          if (ddl == null) {
            log.append("  ! ").append(tbl).append(" 테이블 DDL 없음. PASS.\n");
            continue;
          }
          ddl = convertSqliteDDLtoMysql(ddl, tbl);
          log.append("  + CREATE SQL: ").append(ddl).append("\n");
          try (Statement st = dst.createStatement()) {
            st.execute(ddl);
          }
        } else {
          log.append("  - 기존 테이블 사용: ").append(tbl).append("\n");
        }
        List<String[]> sqliteCols = getSqliteColumns(src, tbl);
        Set<String> mysqlCols = getMysqlColumns(dst, tbl);

        for (String[] col : sqliteCols) {
          String colName = col[0];
          String colType = col[1];
          if (!mysqlCols.contains(colName.toLowerCase())) {
            String mysqlType = TYPE_MAP.getOrDefault(colType, "TEXT");
            String sql = "ALTER TABLE `" + tbl + "` ADD COLUMN `" + colName + "` " + mysqlType + " NULL";
            log.append("  + 컬럼 추가: ").append(sql).append("\n");
            try (Statement st = dst.createStatement()) {
              st.execute(sql);
            }
            mysqlCols.add(colName.toLowerCase());
          }
        }
        int rowCount = copyData(src, dst, tbl, sqliteCols);
        log.append("  * 총 ").append(rowCount).append("개 행 (중복 무시)\n");
      }
      log.append("\n마이그레이션 완료! XAMPP를 재시작 해주세요.\n");
    } catch (Exception e) {
      log.append("[마이그레이션 오류]\n").append(e.getMessage()).append("\n");
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      log.append(sw.toString());
    }
    return log.toString();
  }

  private List<String> getSqliteTables(Connection src) throws SQLException {
    List<String> tables = new ArrayList<>();
    try (Statement st = src.createStatement();
        ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';")) {
      while (rs.next()) tables.add(rs.getString(1));
    }
    return tables;
  }

  private boolean mysqlTableExists(Connection dst, String tbl) throws SQLException {
    try (PreparedStatement ps = dst.prepareStatement(
        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
      ps.setString(1, "recordon"); // 실제 사용 DB명
      ps.setString(2, tbl);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }
    }
  }

  private String getSqliteDDL(Connection src, String tbl) throws SQLException {
    try (PreparedStatement ps = src.prepareStatement(
        "SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) {
      ps.setString(1, tbl);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getString(1);
      }
    }
    return null;
  }

  private String convertSqliteDDLtoMysql(String ddl, String tbl) {
    ddl = ddl.replaceAll("AUTOINCREMENT", "AUTO_INCREMENT");
    ddl = ddl.replaceAll("\\bINTEGER\\b", "INT(11)");
    ddl = ddl.replaceAll("\\bREAL\\b", "DOUBLE");
    ddl = ddl.replaceAll("\\bTEXT\\b", "TEXT");
    ddl = ddl.replaceAll("\\bBLOB\\b", "BLOB");
    ddl = ddl.replaceAll("\"([A-Za-z0-9_]+)\"", "`$1`");
    ddl = ddl.replaceAll("\\);?$", ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
    return ddl;
  }

  private List<String[]> getSqliteColumns(Connection src, String tbl) throws SQLException {
    List<String[]> cols = new ArrayList<>();
    try (Statement st = src.createStatement();
        ResultSet rs = st.executeQuery("PRAGMA table_info('" + tbl + "');")) {
      while (rs.next()) {
        cols.add(new String[]{rs.getString("name"), rs.getString("type").toUpperCase()});
      }
    }
    return cols;
  }

  private Set<String> getMysqlColumns(Connection dst, String tbl) throws SQLException {
    Set<String> cols = new HashSet<>();
    try (PreparedStatement ps = dst.prepareStatement(
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=?")) {
      ps.setString(1, "recordon");
      ps.setString(2, tbl);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) cols.add(rs.getString(1).toLowerCase());
      }
    }
    return cols;
  }

  private int copyData(Connection src, Connection dst, String tbl, List<String[]> sqliteCols) throws SQLException {
    List<String> cols = new ArrayList<>();
    for (String[] arr : sqliteCols) cols.add(arr[0]);
    String colList = String.join("`, `", cols);
    String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
    String insertSql = "INSERT IGNORE INTO `" + tbl + "` (`" + colList + "`) VALUES (" + placeholders + ")";
    String selectSql = "SELECT * FROM `" + tbl + "`";

    int total = 0;
    try (Statement st = src.createStatement();
        ResultSet rs = st.executeQuery(selectSql)) {
      List<Object[]> batch = new ArrayList<>();
      while (rs.next()) {
        Object[] row = new Object[cols.size()];
        for (int i = 0; i < cols.size(); ++i) {
          row[i] = rs.getObject(cols.get(i));
        }
        batch.add(row);
      }
      total = batch.size();
      if (!batch.isEmpty()) {
        try (PreparedStatement ps = dst.prepareStatement(insertSql)) {
          for (Object[] row : batch) {
            for (int i = 0; i < row.length; ++i) {
              ps.setObject(i + 1, row[i]);
            }
            ps.addBatch();
          }
          ps.executeBatch();
        }
      }
    }
    return total;
  }
}
