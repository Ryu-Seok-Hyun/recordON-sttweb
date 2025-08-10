// src/main/java/com/sttweb/sttweb/service/SttSearchService.java
package com.sttweb.sttweb.service;

import com.sttweb.sttweb.dto.SttSearchDtos.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.*;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttSearchService {

  private final RestHighLevelClient esClient;

  private static final String INDEX = "record-stt";

  // 텍스트 후보 필드
  private static final String[] TEXT_FIELDS = new String[] {
      "result.merged.text",
      "result.rx.hypothesis.text",
      "result.tx.hypothesis.text",
      "text"
  };

  // SttSearchService.java
  // SttSearchService.java (빈 검색어 처리만 교체)
  public Page search(String q, int page, int size) {
    try {
      var source = new SearchSourceBuilder()
          .from(Math.max(page, 0) * Math.max(size, 1))
          .size(Math.max(size, 1))
          .trackTotalHits(true);

      if (q == null || q.isBlank()) {
        // 파라미터 없을 때 목록 노출
        source.query(QueryBuilders.matchAllQuery());
        // 정렬 필드가 있다면 여기에 추가 (예: 생성일 최신순)
        // source.sort("crtime", SortOrder.DESC);

        var resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
        var hits = Arrays.stream(resp.getHits().getHits())
            .map(this::toHit)
            .collect(Collectors.toList());
        long total = resp.getHits().getTotalHits().value;
        return new Page(hits, total, page, size);
      }

      // ▼ 기존 검색 로직 (q 있을 때)
      var should = new ArrayList<QueryBuilder>();
      should.add(QueryBuilders.matchQuery("filename", q)
          .operator(Operator.AND)
          .fuzziness(Fuzziness.AUTO)
          .boost(2.0f));
      should.add(QueryBuilders.multiMatchQuery(q, TEXT_FIELDS)
          .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
          .operator(Operator.AND)
          .minimumShouldMatch("70%")
          .fuzziness(Fuzziness.AUTO));

      var bool = QueryBuilders.boolQuery();
      for (QueryBuilder qb : should) bool.should(qb);
      bool.minimumShouldMatch(1);
      source.query(bool);

      var hb = new HighlightBuilder()
          .preTags("<em>").postTags("</em>")
          .fragmentSize(120).numOfFragments(3);
      for (String f : TEXT_FIELDS) hb.field(new HighlightBuilder.Field(f));
      hb.field(new HighlightBuilder.Field("filename"));
      source.highlighter(hb);

      var resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
      if (resp.status() != RestStatus.OK) return new Page(List.of(), 0, page, size);

      var hits = Arrays.stream(resp.getHits().getHits()).map(this::toHit).collect(Collectors.toList());
      long total = resp.getHits().getTotalHits().value;
      return new Page(hits, total, page, size);

    } catch (Exception e) {
      return new Page(List.of(), 0, page, size);
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

  private Hit toHit(SearchHit hit) {
    var src = hit.getSourceAsMap();
    String filename = Objects.toString(src.getOrDefault("filename", ""), "");
    String textSample = extractFirstText(src);

    List<String> hl = hit.getHighlightFields() == null ? List.of() :
        hit.getHighlightFields().values().stream()
            .flatMap(f -> Arrays.stream(f.getFragments()))
            .map(Object::toString)
            .collect(Collectors.toList());

    return new Hit(hit.getId(), filename, textSample, hl, (double) hit.getScore());
  }

  @SuppressWarnings("unchecked")
  private String extractFirstText(Map<String, Object> src) {
    for (String field : TEXT_FIELDS) {
      Object val = dig(src, field);
      if (val instanceof String s && !s.isBlank()) {
        return s.length() > 240 ? s.substring(0, 240) + "..." : s;
      }
    }
    return "";
  }

  @SuppressWarnings("unchecked")
  private Object dig(Map<String, Object> m, String dotted) {
    String[] parts = dotted.split("\\.");
    Object cur = m;
    for (String p : parts) {
      if (!(cur instanceof Map)) return null;
      cur = ((Map<String, Object>) cur).get(p);
      if (cur == null) return null;
    }
    return cur;
  }
}
