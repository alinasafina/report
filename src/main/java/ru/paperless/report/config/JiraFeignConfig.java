package ru.paperless.report.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JiraFeignConfig {

    @Bean
    public RequestInterceptor jiraAuthInterceptor(JiraProperties props) {
        return template -> template.header("Authorization", "Bearer " + props.getPat());
    }
}