package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTokenTest {

    @Test
    void encodeAndDecodeShouldRoundTrip() {
        CursorToken token = new CursorToken(java.time.LocalDateTime.of(2026, 2, 24, 12, 0, 1).atOffset(ZoneOffset.UTC), 123L);
        String encoded = CursorToken.encode(token);

        CursorToken decoded = CursorToken.decodeOrThrow(encoded);
        assertThat(decoded).isEqualTo(token);
    }

    @Test
    void decodeShouldThrowValidationExceptionWhenCursorIsInvalid() {
        assertThatThrownBy(() -> CursorToken.decodeOrThrow("not-a-valid-base64"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void decodeShouldSupportLegacyLocalDateTimeCursor() {
        String legacyCursor = Base64.getUrlEncoder()
                .encodeToString("2026-02-24T12:00:01|123".getBytes());

        CursorToken decoded = CursorToken.decodeOrThrow(legacyCursor);

        assertThat(decoded.getPublishedAt().toLocalDateTime())
                .isEqualTo(java.time.LocalDateTime.of(2026, 2, 24, 12, 0, 1));
        assertThat(decoded.getPostId()).isEqualTo(123L);
    }
}
