package com.group4.kaoyan.common;

import lombok.Data;

import java.util.List;

/**
 * 统一响应体。
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "ok";
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
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }

    /** 分页结果包装。 */
    @Data
    public static class PageData<T> {
        private long total;
        private List<T> records;

        public static <T> PageData<T> of(long total, List<T> records) {
            PageData<T> p = new PageData<>();
            p.total = total;
            p.records = records;
            return p;
        }
    }
}
