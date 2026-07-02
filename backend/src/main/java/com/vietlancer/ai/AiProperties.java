package com.vietlancer.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String classifier, String claudeModel, String anthropicApiKey) {}
