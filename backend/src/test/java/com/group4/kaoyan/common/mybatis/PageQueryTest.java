package com.group4.kaoyan.common.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageQueryTest {

    @Test
    void shouldComputeOffset() {
        PageQuery page = PageQuery.of(3, 20);
        assertEquals(40, page.getOffset());
        assertEquals(20, page.getLimit());
    }

    @Test
    void shouldNormalizeInvalidInput() {
        PageQuery page = PageQuery.of(-1, 0);
        assertEquals(1, page.getPageNum());
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, page.getPageSize());
    }

    @Test
    void shouldCapPageSize() {
        assertEquals(PageQuery.MAX_PAGE_SIZE, PageQuery.of(1, 1000).getPageSize());
    }
}
