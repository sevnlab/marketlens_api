package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
//                .cors(cors -> cors.disable()) // CORS 활성화
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화 (POST 요청 허용)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 비활성화

                // 요청별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 경로 설정
                        .requestMatchers("/signIn", "/signUp", "/oauth/kakao", "/oauth/naver", "/oauth2/callback/naver"
//                                ,
//                                "/oauth2/callback/naver", "/favicon.ico", "/error"
                        ).permitAll() // 해당 경로 인증 제외

                        // 테스트 중인 컨트롤러 API 전부 허용
                        .requestMatchers("/account/**").permitAll()  // 비동기 테스트 
                        .requestMatchers("/redis/**").permitAll() // 레디스 테스트
                        .requestMatchers("/api/queue/stream").permitAll() // SSE (JWT는 컨트롤러에서 직접 검증)

                        .anyRequest().authenticated() // 그 외 나머지 모든 요청은 인증 필요
                );

        // JwtAuthenticationFilter 등을 추가하려면 여기에 추가
        // http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCrypt 암호화 방식 사용
    }
}
