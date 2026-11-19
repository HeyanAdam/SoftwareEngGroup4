package com.group4.kaoyan.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 统一响应体（契约见《接口设计文档 V1.1》第 1.5、1.7 节）。
 *
 * <p>成功码固定 0、message 固定 {@code success}；分页数据固定
 * {@code list / total / page_num / page_size}，与文档的 snake_case 一致。</p>
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(ErrorCode.SERVER_ERROR, message);
    }

    /** 分页结果包装，字段名与文档 1.7 节一一对应。 */
    @Data
    public static class PageData<T> {

        @JsonProperty("list")
        private List<T> list;

        private long total;

        @JsonProperty("page_num")
        private long pageNum;

        @JsonProperty("page_size")
        private long pageSize;

        public static <T> PageData<T> of(long total, List<T> list, long pageNum, long pageSize) {
            PageData<T> p = new PageData<>();
            p.total = total;
            p.list = list;
            p.pageNum = pageNum;
            p.pageSize = pageSize;
            return p;
        }
    }
}
