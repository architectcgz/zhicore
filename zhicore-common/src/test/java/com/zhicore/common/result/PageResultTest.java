package com.zhicore.common.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageResultTest {

    @Test
    void shouldNotReportNextPageWhenFirstPageAlreadyCoversAllData() {
        PageResult<Integer> page = PageResult.of(0, 30, 20, List.of(1, 2, 3));

        assertFalse(page.isHasNext());
    }

    @Test
    void shouldReportNextPageWhenMorePagesRemain() {
        PageResult<Integer> page = PageResult.of(0, 10, 25, List.of(1, 2, 3));

        assertTrue(page.isHasNext());
    }
}
