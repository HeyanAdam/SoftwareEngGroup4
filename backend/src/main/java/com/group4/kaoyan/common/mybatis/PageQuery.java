package com.group4.kaoyan.common.mybatis;

/**
 * 手写分页参数。
 *
 * <p>不引入 PageHelper：新查询数量有限，统一在 XML 里写
 * {@code LIMIT #{offset}, #{size}} 并配独立的 count 语句，行为可预期。</p>
 */
public class PageQuery {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    private int pageNum;
    private int pageSize;

    public PageQuery() {
        this.pageNum = 1;
        this.pageSize = DEFAULT_PAGE_SIZE;
    }

    public PageQuery(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        normalize();
    }

    /** 兜底归一化：页码最小 1，页大小 1–100。 */
    public static PageQuery of(long pageNum, long pageSize) {
        return new PageQuery((int) pageNum, (int) pageSize);
    }

    public void normalize() {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
    }

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

    public int getLimit() {
        return pageSize;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
        normalize();
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        normalize();
    }
}
