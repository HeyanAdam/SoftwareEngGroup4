package com.group4.kaoyan.platform.ai.llm;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话模型客户端。模型名、地址、密钥全部来自配置，换模型只改环境变量。
 */
public interface LlmClient {

    record Msg(String role, String content) {
        public static Msg system(String c) {
            return new Msg("system", c);
        }

        public static Msg user(String c) {
            return new Msg("user", c);
        }

        public static Msg assistant(String c) {
            return new Msg("assistant", c);
        }
    }

    /** 流式输出，返回内容增量 */
    Flux<String> streamChat(List<Msg> messages);

    /** 非流式输出 */
    String chat(List<Msg> messages);
}
