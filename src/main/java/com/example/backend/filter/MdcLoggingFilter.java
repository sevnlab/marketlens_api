package com.example.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 HTTP 요청에 고유한 추적번호(traceId)를 붙여주는 필터
 *
 * MDC(Mapped Diagnostic Context) : 로그에 추가 정보를 심는 SLF4J 기능
 * → 같은 traceId를 가진 로그를 모으면 요청 하나의 전체 흐름을 추적할 수 있음
 *
 * ex) [traceId=a3f9b2c1] INFO  UserController - 로그인 시도: hong
 *     [traceId=a3f9b2c1] DEBUG UserService    - DB 조회 시작
 *     [traceId=a3f9b2c1] INFO  UserController - 로그인 성공: hong
 *     → traceId가 같으니까 이 세 줄이 같은 요청이라는 걸 바로 알 수 있음
 */
@Component  // Spring이 이 클래스를 자동으로 빈으로 등록하고 필터로 동작시킴
@Order(1)   // 필터 순서: 1번 → 가장 먼저 실행 (traceId를 제일 먼저 심어야 모든 로그에 찍힘)
public class MdcLoggingFilter extends OncePerRequestFilter {
    // OncePerRequestFilter : 같은 요청에서 이 필터가 딱 한 번만 실행되도록 보장하는 부모 클래스

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // UUID 앞 8자리만 사용 (ex: a3f9b2c1)
            // 너무 길면 로그 보기 불편해서 8자리로 자름
            String traceId = UUID.randomUUID().toString().substring(0, 8);

            // MDC에 traceId 저장 → 이 스레드에서 찍히는 모든 로그에 자동으로 포함됨
            // log4j2-spring.xml 의 [%X{traceId}] 부분이 여기서 꺼내감
            MDC.put("traceId", traceId);

            // 다음 필터 또는 컨트롤러로 요청을 넘김
            filterChain.doFilter(request, response);

        } finally {
            // 요청 처리가 끝나면 반드시 MDC를 비워줘야 함
            // 비우지 않으면 스레드 풀에서 스레드가 재사용될 때 이전 traceId가 남아있는 문제 발생
            MDC.clear();
        }
    }
}