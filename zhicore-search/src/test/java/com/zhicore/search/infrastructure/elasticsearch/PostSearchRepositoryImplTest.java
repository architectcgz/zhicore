package com.zhicore.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import jakarta.json.stream.JsonGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostSearchRepositoryImpl 索引初始化测试")
class PostSearchRepositoryImplTest {

    private static final JsonpMapper JSONP_MAPPER = new JacksonJsonpMapper();

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Test
    @DisplayName("缺少分析插件时应回退到无插件 schema 重建索引")
    void initIndexShouldFallbackWhenAnalysisPluginsAreUnavailable() throws IOException {
        when(esClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
        when(indicesClient.create(any(CreateIndexRequest.class)))
                .thenThrow(missingPinyinPluginException())
                .thenReturn(CreateIndexResponse.of(builder -> builder
                        .index(PostSearchRepositoryImpl.INDEX_NAME)
                        .acknowledged(true)
                        .shardsAcknowledged(true)));

        PostSearchRepositoryImpl repository = new PostSearchRepositoryImpl(esClient);

        assertDoesNotThrow(repository::initIndex);

        ArgumentCaptor<CreateIndexRequest> requestCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient, times(2)).create(requestCaptor.capture());

        String primaryRequestJson = toJson(requestCaptor.getAllValues().get(0));
        String fallbackRequestJson = toJson(requestCaptor.getAllValues().get(1));

        assertThat(primaryRequestJson).contains("pinyin_filter");
        assertThat(primaryRequestJson).contains("ik_smart");

        assertThat(fallbackRequestJson).doesNotContain("pinyin_filter");
        assertThat(fallbackRequestJson).doesNotContain("ik_smart");
        assertThat(fallbackRequestJson).contains("\"pinyin\"");
        assertThat(fallbackRequestJson).contains("\"type\":\"nested\"");
    }

    private ElasticsearchException missingPinyinPluginException() {
        return new ElasticsearchException(
                "es/indices.create",
                ErrorResponse.of(builder -> builder
                        .status(400)
                        .error(error -> error
                                .type("illegal_argument_exception")
                                .reason("Unknown filter type [pinyin] for [pinyin_filter]"))));
    }

    private String toJson(CreateIndexRequest request) {
        StringWriter writer = new StringWriter();
        JsonGenerator generator = JSONP_MAPPER.jsonProvider().createGenerator(writer);
        request.serialize(generator, JSONP_MAPPER);
        generator.close();
        return writer.toString();
    }
}
