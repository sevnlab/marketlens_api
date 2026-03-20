package com.marketlens.filter;

import com.marketlens.config.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 인증 필터
 *
 * 모든 요청에서 쿠키("jwt")를 꺼내 토큰이 유효하면 SecurityContext에 인증 정보 등록.
 * 이후 컨트롤러에서 Authentication 파라미터로 로그인한 사용자 정보를 바로 받을 수 있음.
 *
 * 토큰이 없거나 유효하지 않으면 아무것도 하지 않음 (인증 안 된 상태로 통과)
 * → 인증이 필요한 API는 SecurityConfig 또는 컨트롤러에서 별도로 체크
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromCookie(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUsernameFromJWT(token);

            // SecurityContext에 인증 정보 등록
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[JWT] 인증 완료 - userId={}", userId);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 쿠키 배열에서 "jwt" 이름의 쿠키 값 추출
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}