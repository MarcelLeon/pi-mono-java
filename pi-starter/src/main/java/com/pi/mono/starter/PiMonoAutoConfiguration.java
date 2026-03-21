package com.pi.mono.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Pi-Mono Java Spring Boot 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(PiMonoProperties.class)
@ComponentScan(basePackages = {
    "com.pi.mono.core",
    "com.pi.mono.llm",
    "com.pi.mono.session",
    "com.pi.mono.tools"
})
public class PiMonoAutoConfiguration {
}
