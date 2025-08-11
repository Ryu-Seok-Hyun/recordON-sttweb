// src/main/java/com/sttweb/sttweb/service/SttSearchService.java
package com.sttweb.sttweb.service;

import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.*;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttSearchService {

  private final RestHighLevelClient esClient;
  private static final String INDEX = "record-stt";

  /** 텍스트 매칭된 filename 유니크만 반환 (collapse 사용) */
  public Map<String, Object> searchFilenames(String q, int page, int size) {
    int from = Math.max(page, 0) * Math.max(size, 1);
    int sz   = Math.max(size, 1);

    try {
      BoolQueryBuilder bool = QueryBuilders.boolQuery();

      if (q == null || q.isBlank()) {
        bool.must(QueryBuilders.matchAllQuery());
      } else {
        // filename 정확/부분 매치
        bool.should(QueryBuilders.termQuery("filename", q).boost(4.0f));
        bool.should(QueryBuilders.wildcardQuery("filename", "*" + q + "*").boost(1.0f));

        // 본문 nested 접두 매치
        bool.should(QueryBuilders.nestedQuery(
            "result.merged",
            QueryBuilders.matchPhrasePrefixQuery("result.merged.text", q),
            ScoreMode.Avg));

        bool.should(QueryBuilders.nestedQuery(
            "result.rx.hypothesis.sentences",
            QueryBuilders.matchPhrasePrefixQuery("result.rx.hypothesis.sentences.text", q),
            ScoreMode.Avg));

        bool.should(QueryBuilders.nestedQuery(
            "result.tx.hypothesis.sentences",
            QueryBuilders.matchPhrasePrefixQuery("result.tx.hypothesis.sentences.text", q),
            ScoreMode.Avg));

        bool.minimumShouldMatch(1);
      }

      SearchSourceBuilder source = new SearchSourceBuilder()
          .query(bool)
          .from(from)
          .size(sz)
          .trackTotalHits(true)
          .fetchSource(new String[] { "filename" }, new String[] {})
          // ★ 매핑상 filename이 이미 keyword 타입 → .keyword 아님!
          .collapse(new CollapseBuilder("filename"));

      SearchResponse resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
      if (resp.status() != RestStatus.OK) {
        return Map.of("filenames", List.of(), "total", 0, "page", page, "size", sz);
      }

      List<String> filenames = Arrays.stream(resp.getHits().getHits())
          .map(SearchHit::getSourceAsMap)
          .filter(Objects::nonNull)
          .map(m -> Objects.toString(m.getOrDefault("filename", ""), ""))
          .filter(s -> !s.isBlank())
          .collect(Collectors.toList());

      long total = resp.getHits().getTotalHits().value; // collapse 시 유니크 총합이 아님(참고)

      return Map.of(
          "page", page,
          "size", sz,
          "total", total,
          "filenames", filenames
      );

    } catch (Exception e) {
      return Map.of("filenames", List.of(), "total", 0, "page", page, "size", sz);
    }
  }

  public Map<String, Object> getById(String id) {
    try {
      var source = new SearchSourceBuilder()
          .query(QueryBuilders.idsQuery().addIds(id))
          .size(1);

      var resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
      if (resp.getHits().getHits().length == 0) return Map.of();

      return resp.getHits().getHits()[0].getSourceAsMap();
    } catch (Exception e) {
      return Map.of();
    }
  }
}
