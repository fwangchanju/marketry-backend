package kr.co.marketry.domain.renderer.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "renderer")
public record RendererProperties(String url, boolean ownerCaptureEnabled) {}
