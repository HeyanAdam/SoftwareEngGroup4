package com.group4.kaoyan.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P2 任务 4：401/403 与业务码映射测试（契约见接口文档 V1.1 第 2.1 节）。
 */
@DisplayName("P2-全局异常与错误码")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertCode(ResponseEntity<R<Void>> resp, int code, HttpStatus http) {
        assertEquals(http, resp.getStatusCode());
        assertEquals(code, resp.getBody().getCode());
    }

    @Test
    @DisplayName("无接口权限：403 + 10008")
    void noPermission() {
        assertCode(handler.handleBiz(new BizException(ErrorCode.NO_PERMISSION, "无接口访问权限")),
                ErrorCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("越权访问他人数据：403 + 10009")
    void noDataAccess() {
        assertCode(handler.handleBiz(new BizException(ErrorCode.NO_DATA_ACCESS, "无权访问该数据")),
                ErrorCode.NO_DATA_ACCESS, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("未登录：401 + 10001")
    void notLogin() {
        assertCode(handler.handleBiz(new BizException(ErrorCode.NOT_LOGIN, "未登录或登录已失效")),
                ErrorCode.NOT_LOGIN, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("限流：429 + 10010")
    void tooManyRequests() {
        assertCode(handler.handleBiz(new BizException(ErrorCode.TOO_MANY_REQUESTS, "操作过于频繁")),
                ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("未处理异常：500 + 90000，且不把堆栈细节回给前端")
    void serverError() {
        ResponseEntity<R<Void>> resp = handler.handleOther(new IllegalStateException("connection refused: 3306"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals(ErrorCode.SERVER_ERROR, resp.getBody().getCode());
        assertEquals("服务端异常，请联系管理员", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("历史写法 new BizException(401, ...) 仍然按 401 返回，不被改写成 400")
    void legacyHttpStyleCode() {
        assertCode(handler.handleBiz(new BizException(401, "用户名或密码错误")),
                401, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("业务码与 HTTP 状态映射表")
    void mapping() {
        assertEquals(400, ErrorCode.httpStatusOf(ErrorCode.PARAM_INVALID));
        assertEquals(401, ErrorCode.httpStatusOf(ErrorCode.NOT_LOGIN));
        assertEquals(423, ErrorCode.httpStatusOf(ErrorCode.ACCOUNT_LOCKED));
        assertEquals(404, ErrorCode.httpStatusOf(ErrorCode.USER_NOT_FOUND));
        assertEquals(403, ErrorCode.httpStatusOf(ErrorCode.NO_PERMISSION));
        assertEquals(403, ErrorCode.httpStatusOf(ErrorCode.NO_DATA_ACCESS));
        assertEquals(429, ErrorCode.httpStatusOf(ErrorCode.TOO_MANY_REQUESTS));
    }
}
