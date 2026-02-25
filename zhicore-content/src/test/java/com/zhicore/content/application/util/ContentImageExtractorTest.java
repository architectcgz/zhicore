package com.zhicore.content.application.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContentImageExtractorTest {

    @Test
    void extractImageUrls_fromHtml_imgSrc() {
        String html = """
                <p>text</p>
                <img src="https://cdn.example.com/api/v1/files/01h00000-0000-7000-8000-000000000000" />
                <img alt='x' src='https://example.com/a.png'>
                <img src=https://example.com/no-quotes.jpg>
                """;

        Set<String> urls = ContentImageExtractor.extractImageUrls(html, "html");

        assertThat(urls).contains(
                "https://cdn.example.com/api/v1/files/01h00000-0000-7000-8000-000000000000",
                "https://example.com/a.png",
                "https://example.com/no-quotes.jpg"
        );
    }

    @Test
    void extractImageUrls_fromMarkdown_imageSyntax() {
        String md = """
                ![alt](https://example.com/a.png)
                ![](https://example.com/b.jpg "title")
                ![](<https://example.com/c.webp>)
                """;

        Set<String> urls = ContentImageExtractor.extractImageUrls(md, "markdown");

        assertThat(urls).contains(
                "https://example.com/a.png",
                "https://example.com/b.jpg",
                "https://example.com/c.webp"
        );
    }
}

