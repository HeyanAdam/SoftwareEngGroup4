package com.group4.kaoyan.modules.knowledge.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Prompt 模板外置在 resources/prompts，改提示词不用改代码。
 */
@Component
public class PromptTemplateLoader implements InitializingBean {

    private static final String PATH = "prompts/rag-system.txt";
    private String template;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (InputStream in = new ClassPathResource(PATH).getInputStream()) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String render(String context, String history, String question) {
        return template
                .replace("{context}", context == null ? "" : context)
                .replace("{history}", history == null ? "" : history)
                .replace("{question}", question == null ? "" : question);
    }
}
