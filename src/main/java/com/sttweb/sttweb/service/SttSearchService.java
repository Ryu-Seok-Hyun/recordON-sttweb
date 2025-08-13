package com.sttweb.sttweb.service;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SttSearchService {

  private final RestHighLevelClient esClient;
  private static final String INDEX = "record-stt";

  /** 공통: 텍스트(q)를 현 매핑(object) 기준으로 검색하는 BoolQuery */
  private BoolQueryBuilder buildTextQuery(String q) {
    BoolQueryBuilder bool = QueryBuilders.boolQuery();

    if (q == null || q.isBlank()) {
      bool.must(QueryBuilders.matchAllQuery());
      return bool;
    }

    // filename 정확/부분
    bool.should(QueryBuilders.termQuery("filename.keyword", q).boost(4.0f));
    bool.should(QueryBuilders.wildcardQuery("filename", "*" + q + "*").boost(1.0f));

    // 본문(현 매핑은 object이므로 nestedQuery 금지)
    bool.should(QueryBuilders.matchPhrasePrefixQuery("result.merged.text", q).boost(3.0f));
    bool.should(QueryBuilders.matchPhrasePrefixQuery("result.rx.hypothesis.sentences.text", q).boost(2.0f));
    bool.should(QueryBuilders.matchPhrasePrefixQuery("result.tx.hypothesis.sentences.text", q).boost(2.0f));

    bool.minimumShouldMatch(1);
    return bool;
  }

  /** 텍스트 매칭된 filename 유니크만 반환 (collapse 사용) */
  public Map<String, Object> searchFilenames(String q, int page, int size) {
    int from = Math.max(page, 0) * Math.max(size, 1);
    int sz   = Math.max(size, 1);

    try {
      SearchSourceBuilder source = new SearchSourceBuilder()
          .query(buildTextQuery(q))
          .from(from)
          .size(sz)
          .trackTotalHits(true)
          .fetchSource(new String[] { "filename" }, new String[] {})
          // filename.keyword 로 유니크
          .collapse(new CollapseBuilder("filename.keyword"));

      SearchResponse resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
      if (resp.status() != RestStatus.OK) {
        return Map.of("page", page, "size", sz, "total", 0, "filenames", List.of());
      }

      List<String> filenames = Arrays.stream(resp.getHits().getHits())
          .map(SearchHit::getSourceAsMap)
          .filter(Objects::nonNull)
          .map(m -> Objects.toString(m.getOrDefault("filename", ""), ""))
          .filter(s -> !s.isBlank())
          .collect(Collectors.toList());

      long total = resp.getHits().getTotalHits().value; // collapse 시 실제 유니크 건수와 다를 수 있음(참고)

      return Map.of("page", page, "size", sz, "total", total, "filenames", filenames);

    } catch (Exception e) {
      return Map.of("page", page, "size", sz, "total", 0, "filenames", List.of());
    }
  }

  /** s 배열(OR) → filename 유니크 페이징 */
  public Map<String, Object> searchFilenamesArray(List<String> queries, int page, int size) {
    int from = Math.max(page, 0) * Math.max(size, 1);
    int sz   = Math.max(size, 1);

    try {
      BoolQueryBuilder root = QueryBuilders.boolQuery();

      if (queries == null || queries.isEmpty()) {
        root.must(QueryBuilders.matchAllQuery());
      } else {
        BoolQueryBuilder orQueries = QueryBuilders.boolQuery();
        for (String q : queries) {
          if (q == null || q.isBlank()) continue;
          orQueries.should(buildTextQuery(q));
        }
        orQueries.minimumShouldMatch(1);
        root.must(orQueries);
      }

      SearchSourceBuilder source = new SearchSourceBuilder()
          .query(root)
          .from(from)
          .size(sz)
          .trackTotalHits(true)
          .fetchSource(new String[]{"filename"}, new String[]{})
          .collapse(new CollapseBuilder("filename.keyword"));

      SearchResponse resp = esClient.search(new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);
      if (resp.status() != RestStatus.OK) {
        return Map.of("page", page, "size", sz, "total", 0, "filenames", List.of());
      }

      List<String> filenames = Arrays.stream(resp.getHits().getHits())
          .map(SearchHit::getSourceAsMap)
          .filter(Objects::nonNull)
          .map(m -> Objects.toString(m.getOrDefault("filename", ""), ""))
          .filter(s -> !s.isBlank())
          .collect(Collectors.toList());

      long total = resp.getHits().getTotalHits().value;

      return Map.of("page", page, "size", sz, "total", total, "filenames", filenames);

    } catch (Exception e) {
      return Map.of("page", page, "size", sz, "total", 0, "filenames", List.of());
    }
  }

  /** 단건 원본 문서 */
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
