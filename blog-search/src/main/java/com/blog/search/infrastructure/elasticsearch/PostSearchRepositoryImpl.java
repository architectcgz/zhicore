package com.blog.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.blog.search.domain.model.PostDocument;
import com.blog.search.domain.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章搜索仓储 Elasticsearch 实现
 *
 * @author Blog Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostSearchRepositoryImpl implements PostSearchRepository {

    private final ElasticsearchClient esClient;

    public static final String INDEX_NAME = "posts";

    @Override
    public void index(PostDocument document) {
        try {
            esClient.index(i -> i
                .index(INDEX_NAME)
                .id(String.valueOf(document.getId()))
                .document(document)
            );
            log.debug("Indexed post: id={}", document.getId());
        } catch (IOException e) {
            log.error("Failed to index post: id={}", document.getId(), e);
            throw new RuntimeException("Failed to index post", e);
        }
    }

    @Override
    public void update(PostDocument document) {
        try {
            esClient.update(u -> u
                .index(INDEX_NAME)
                .id(String.valueOf(document.getId()))
                .doc(document),
                PostDocument.class
            );
            log.debug("Updated post index: id={}", document.getId());
        } catch (IOException e) {
            log.error("Failed to update post index: id={}", document.getId(), e);
            throw new RuntimeException("Failed to update post index", e);
        }
    }

    @Override
    public void partialUpdate(String postId, String title, String content, String excerpt) {
        try {
            Map<String, Object> updates = new HashMap<>();
            if (title != null) updates.put("title", title);
            if (content != null) updates.put("content", content);
            if (excerpt != null) updates.put("excerpt", excerpt);

            esClient.update(u -> u
                .index(INDEX_NAME)
                .id(postId)
                .doc(updates),
                PostDocument.class
            );
            log.debug("Partially updated post index: id={}", postId);
        } catch (IOException e) {
            log.error("Failed to partially update post index: id={}", postId, e);
            throw new RuntimeException("Failed to partially update post index", e);
        }
    }

    /**
     * 更新文章标签
     */
    public void updateTags(String postId, List<PostDocument.TagInfo> tags) {
        try {
            Map<String, Object> updates = new HashMap<>();
            updates.put("tags", tags);

            esClient.update(u -> u
                .index(INDEX_NAME)
                .id(postId)
                .doc(updates),
                PostDocument.class
            );
            log.debug("Updated post tags in index: id={}, tagCount={}", postId, tags != null ? tags.size() : 0);
        } catch (IOException e) {
            log.error("Failed to update post tags in index: id={}", postId, e);
            throw new RuntimeException("Failed to update post tags in index", e);
        }
    }

    @Override
    public void delete(String postId) {
        try {
            esClient.delete(d -> d
                .index(INDEX_NAME)
                .id(postId)
            );
            log.debug("Deleted post from index: id={}", postId);
        } catch (IOException e) {
            log.error("Failed to delete post from index: id={}", postId, e);
            throw new RuntimeException("Failed to delete post from index", e);
        }
    }

    @Override
    public Optional<PostDocument> findById(String postId) {
        try {
            GetResponse<PostDocument> response = esClient.get(g -> g
                .index(INDEX_NAME)
                .id(postId),
                PostDocument.class
            );
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (IOException e) {
            log.error("Failed to get post from index: id={}", postId, e);
            throw new RuntimeException("Failed to get post from index", e);
        }
    }

    @Override
    public SearchResult<PostDocument> search(String keyword, int page, int size) {
        try {
            SearchResponse<PostDocument> response = esClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q
                    .bool(b -> b
                        .should(sh -> sh
                            .multiMatch(m -> m
                                .query(keyword)
                                .fields("title^3", "title.pinyin^2", "content", "excerpt")
                                .type(TextQueryType.BestFields)
                                .fuzziness("AUTO")
                            )
                        )
                        .should(sh -> sh
                            .nested(n -> n
                                .path("tags")
                                .query(nq -> nq
                                    .multiMatch(mm -> mm
                                        .query(keyword)
                                        .fields("tags.name^2", "tags.slug")
                                    )
                                )
                            )
                        )
                        .filter(f -> f
                            .term(t -> t.field("status").value("PUBLISHED"))
                        )
                    )
                )
                .highlight(h -> h
                    .fields("title", hf -> hf.preTags("<em>").postTags("</em>"))
                    .fields("content", hf -> hf
                        .preTags("<em>")
                        .postTags("</em>")
                        .fragmentSize(150)
                        .numberOfFragments(3)
                    )
                )
                .from(page * size)
                .size(size)
                .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                .sort(so -> so.field(f -> f.field("publishedAt").order(SortOrder.Desc))),
                PostDocument.class
            );

            List<SearchHit<PostDocument>> hits = response.hits().hits().stream()
                .map(this::convertHit)
                .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return new SearchResult<>(hits, total, page, size);
        } catch (IOException e) {
            log.error("Failed to search posts: keyword={}", keyword, e);
            throw new RuntimeException("Failed to search posts", e);
        }
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        try {
            SearchResponse<PostDocument> response = esClient.search(s -> s
                .index(INDEX_NAME)
                .query(q -> q
                    .bool(b -> b
                        .should(sh -> sh
                            .prefix(p -> p.field("title").value(prefix))
                        )
                        .should(sh -> sh
                            .prefix(p -> p.field("title.pinyin").value(prefix))
                        )
                        .filter(f -> f
                            .term(t -> t.field("status").value("PUBLISHED"))
                        )
                    )
                )
                .size(limit)
                .source(src -> src.filter(sf -> sf.includes("title"))),
                PostDocument.class
            );

            return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(PostDocument::getTitle)
                .distinct()
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to get suggestions: prefix={}", prefix, e);
            throw new RuntimeException("Failed to get suggestions", e);
        }
    }

    @Override
    public void initIndex() {
        try {
            if (indexExists()) {
                log.info("Index {} already exists", INDEX_NAME);
                return;
            }

            String indexSettings = getIndexSettings();
            esClient.indices().create(CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(indexSettings))
            ));
            log.info("Created index: {}", INDEX_NAME);
        } catch (IOException e) {
            log.error("Failed to create index: {}", INDEX_NAME, e);
            throw new RuntimeException("Failed to create index", e);
        }
    }

    @Override
    public boolean indexExists() {
        try {
            return esClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
        } catch (IOException e) {
            log.error("Failed to check index existence: {}", INDEX_NAME, e);
            throw new RuntimeException("Failed to check index existence", e);
        }
    }

    private SearchHit<PostDocument> convertHit(Hit<PostDocument> hit) {
        PostDocument doc = hit.source();
        float score = hit.score() != null ? hit.score().floatValue() : 0f;

        String highlightTitle = null;
        String highlightContent = null;

        if (hit.highlight() != null) {
            Map<String, List<String>> highlights = hit.highlight();
            if (highlights.containsKey("title") && !highlights.get("title").isEmpty()) {
                highlightTitle = String.join("...", highlights.get("title"));
            }
            if (highlights.containsKey("content") && !highlights.get("content").isEmpty()) {
                highlightContent = String.join("...", highlights.get("content"));
            }
        }

        return new SearchHit<>(doc, score, highlightTitle, highlightContent);
    }

    private String getIndexSettings() {
        return """
            {
              "settings": {
                "number_of_shards": 3,
                "number_of_replicas": 1,
                "analysis": {
                  "analyzer": {
                    "ik_smart_pinyin": {
                      "type": "custom",
                      "tokenizer": "ik_smart",
                      "filter": ["lowercase", "pinyin_filter"]
                    }
                  },
                  "filter": {
                    "pinyin_filter": {
                      "type": "pinyin",
                      "keep_full_pinyin": false,
                      "keep_joined_full_pinyin": true,
                      "keep_original": true,
                      "limit_first_letter_length": 16,
                      "remove_duplicated_term": true
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": { "type": "keyword" },
                  "title": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart",
                    "fields": {
                      "pinyin": {
                        "type": "text",
                        "analyzer": "ik_smart_pinyin"
                      }
                    }
                  },
                  "content": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_smart"
                  },
                  "excerpt": { "type": "text", "analyzer": "ik_smart" },
                  "authorId": { "type": "keyword" },
                  "authorName": { "type": "keyword" },
                  "tags": {
                    "type": "nested",
                    "properties": {
                      "id": { "type": "keyword" },
                      "name": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "fields": {
                          "keyword": { "type": "keyword" }
                        }
                      },
                      "slug": { "type": "keyword" }
                    }
                  },
                  "categoryId": { "type": "keyword" },
                  "categoryName": { "type": "keyword" },
                  "status": { "type": "keyword" },
                  "likeCount": { "type": "integer" },
                  "commentCount": { "type": "integer" },
                  "viewCount": { "type": "long" },
                  "publishedAt": { "type": "date" },
                  "createdAt": { "type": "date" },
                  "updatedAt": { "type": "date" }
                }
              }
            }
            """;
    }
}
