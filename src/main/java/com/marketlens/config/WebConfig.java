package com.marketlens.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
//        registry.addMapping("/**") // 경로에 대해서 CORS 설정
////                .allowedOrigins("http://localhost:3000") // 리액트 애플리케이션의 도메인을 설정
//                .allowedOrigins("*") // 리액트 애플리케이션의 도메인을 설정
////                .allowedMethods("GET", "POST", "PUT", "DELETE")
//                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
//                .allowedHeaders("Authorization", "Content-Type")
//                .exposedHeaders("Custom-Header")
//                .allowCredentials(true) // 인증 정보를 요청 헤더에 포함시킬지 여부
//                .maxAge(3600); // 캐싱 시간 (초 단위)
////        registry.addMapping("/v1/reviews/**") // v1/reviews/ 경로에 대해서 CORS 설정
////                .allowedOrigins("http://localhost:3000") // // 클라이언트의 주소
////                .allowedMethods("GET", "POST", "PUT", "DELETE")
////                .allowCredentials(true);
    }
}
