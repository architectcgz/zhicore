package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTokenTest {

    @Test
    void encodeAndDecodeShouldRoundTrip() {
        CursorToken token = new CursorToken(LocalDateTime.of(2026, 2, 24, 12, 0, 1), 123L);
        String encoded = CursorToken.encode(token);

        CursorToken decoded = CursorToken.decodeOrThrow(encoded);
        assertThat(decoded).isEqualTo(token);
    }

    @Test
    void decodeShouldThrowValidationExceptionWhenCursorIsInvalid() {
        assertThatThrownBy(() -> CursorToken.decodeOrThrow("not-a-valid-base64"))
                .isInstanceOf(ValidationException.class);
    }
}

