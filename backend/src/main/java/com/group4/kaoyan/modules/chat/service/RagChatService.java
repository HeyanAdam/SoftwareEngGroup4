package com.group4.kaoyan.modules.chat.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group4.kaoyan.common.BizException;
import com.group4.kaoyan.common.config.AiProperties;
import com.group4.kaoyan.common.security.RoleConstants;
import com.group4.kaoyan.modules.chat.entity.ChatConversation;
import com.group4.kaoyan.modules.chat.entity.ChatMessage;
import com.group4.kaoyan.modules.knowledge.service.PromptTemplateLoader;
import com.group4.kaoyan.modules.knowledge.service.RetrievalService;
import com.group4.kaoyan.platform.ai.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答主流程：检索 → 组 Prompt → 流式生成 → SSE 下发 → 落库（含引用与模型名）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final ChatService chatService;
    private final RetrievalService retrievalService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmClient llmClient;
    private final AiProperties props;
    private final ObjectMapper objectMapper;

    public SseEmitter chat(Long userId, Long conversationId, Long kbId, String question) {
        if (question == null || question.isBlank()) {
            throw new BizException("问题不能为空");
        }
        ChatConversation conv = chatService.getOrCreate(userId, conversationId, kbId, question);
        String history = chatService.recentHistory(conv.getId());
        chatService.saveUserMessage(conv, userId, question);

        SseEmitter emitter = new SseEmitter(0L);
        send(emitter, "meta", Map.of("conversationId", conv.getId(), "model", props.getLlm().getModel()));
        try {
            // 普通用户不认 kbId，只能检索公开库；管理员可指定库
            Long filterKbId = StpUtil.hasRole(RoleConstants.SYSTEM_ADMIN) ? conv.getKbId() : null;
            List<RetrievalService.Retrieved> hits = retrievalService.retrieve(filterKbId, question);
            send(emitter, "references", toRefs(hits));

            String prompt = promptTemplateLoader.render(retrievalService.buildContext(hits), history, question);
            List<LlmClient.Msg> messages = List.of(
                    LlmClient.Msg.system(prompt), LlmClient.Msg.user(question));
            StringBuilder answer = new StringBuilder();

            llmClient.streamChat(messages).subscribe(
                    delta -> {
                        answer.append(delta);
                        send(emitter, "delta", Map.of("content", delta));
                    },
                    error -> {
                        log.warn("流式生成失败：{}", error.getMessage());
                        finish(emitter, conv, userId, answer.toString(), hits, "生成中断：" + error.getMessage());
                    },
                    () -> finish(emitter, conv, userId, answer.toString(), hits, null)
            );
        } catch (Exception e) {
            log.error("RAG 问答失败", e);
            send(emitter, "error", Map.of("message", String.valueOf(e.getMessage())));
            emitter.complete();
        }
        return emitter;
    }

    private void finish(SseEmitter emitter, ChatConversation conv, Long userId, String answer,
                        List<RetrievalService.Retrieved> hits, String error) {
        try {
            ChatMessage saved = chatService.saveAssistantMessage(
                    conv, userId, answer, toJson(toRefs(hits)), props.getLlm().getModel());
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("messageId", saved.getId());
            done.put("error", error);
            send(emitter, "done", done);
        } catch (Exception e) {
            log.warn("保存回答失败：{}", e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    /**
     * 引用信息：管理员可见文档 id、切片 id、相似度、片段摘要；
     * 普通用户完全不可见（返回空数组），只能看到自然语言回答本身。
     */
    private List<Map<String, Object>> toRefs(List<RetrievalService.Retrieved> hits) {
        if (!StpUtil.hasRole(RoleConstants.SYSTEM_ADMIN)) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            RetrievalService.Retrieved r = hits.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i + 1);
            m.put("docName", r.docName());
            m.put("docId", r.docId());
            m.put("chunkId", r.chunkId());
            m.put("score", r.score());
            m.put("snippet", r.content().length() > 120 ? r.content().substring(0, 120) : r.content());
            list.add(m);
        }
        return list;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("SSE 推送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }
}
