package com.marketlens.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {
    @Bean
    public CustomCorsFilter corsFilter() {
        return new CustomCorsFilter();
    }
}
