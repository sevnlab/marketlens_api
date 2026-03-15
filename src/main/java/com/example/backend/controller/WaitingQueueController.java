package com.example.backend.controller;

import com.example.backend.config.JwtTokenProvider;
import com.example.backend.dto.ApiResponse;
import com.example.backend.service.SseEmitterService;
import com.example.backend.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 대기열 컨트롤러 (운영 환경 전용)
 *
 * 흐름:
 *   1. 로그인 성공 후 JWT 토큰 보유한 유저가
 *   2. POST /api/queue/enter 로 대기열 진입
 *   3. GET  /api/queue/status 로 내 순번 조회
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queue")
@Profile("real")
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;
    private final SseEmitterService sseEmitterService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 대기열 진입
     *
     * Request Header: Authorization: Bearer {JWT토큰}
     * Response: { success: true, data: { rank: 3, total: 100 } }
     */
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<?>> enter(
            @RequestHeader("Authorization") String authHeader) {

        String userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("유효하지 않은 토큰입니다."));
        }

        boolean isNew = waitingQueueService.enter(userId);
        long rank = waitingQueueService.getRank(userId);
        long total = waitingQueueService.getSize();

        String message = isNew ? "대기열에 등록되었습니다." : "이미 대기열에 등록되어 있습니다.";
        log.info("[대기열 진입] userId={}, rank={}, total={}", userId, rank, total);

        return ResponseEntity.ok(ApiResponse.success(message,
                Map.of("rank", rank, "total", total)));
    }

    /**
     * 내 순번 조회
     *
     * Request Header: Authorization: Bearer {JWT토큰}
     * Response: { success: true, data: { rank: 3, total: 100 } }
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<?>> status(
            @RequestHeader("Authorization") String authHeader) {

        String userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("유효하지 않은 토큰입니다."));
        }

        long rank = waitingQueueService.getRank(userId);
        if (rank == -1) {
            return ResponseEntity.ok(ApiResponse.fail("대기열에 등록되지 않은 사용자입니다."));
        }

        long total = waitingQueueService.getSize();
        return ResponseEntity.ok(ApiResponse.success("조회 성공",
                Map.of("rank", rank, "total", total)));
    }

    /**
     * SSE 구독 (실시간 순번/입장 알림)
     *
     * 클라이언트는 이 연결을 열어두면 서버가 알아서 push해줌:
     *   - event: rank     → 현재 순번 정보 (스케줄러가 주기적으로 전송)
     *   - event: admitted → 입장 허용 + 입장 토큰 (Kafka Consumer가 처리 완료 후 전송)
     *
     * produces = TEXT_EVENT_STREAM_VALUE: SSE 전용 Content-Type 지정 필수
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader("Authorization") String authHeader) {

        String userId = extractUserId(authHeader);
        if (userId == null) {
            // SSE는 ResponseEntity 반환이 안 되므로 연결 즉시 에러 전송 후 종료
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalArgumentException("유효하지 않은 토큰입니다."));
            return emitter;
        }

        log.info("[SSE] 구독 요청 - userId={}", userId);
        return sseEmitterService.connect(userId);
    }

    /**
     * 대기열 취소 (자발적 이탈)
     *
     * Request Header: Authorization: Bearer {JWT토큰}
     */
    @DeleteMapping("/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(
            @RequestHeader("Authorization") String authHeader) {

        String userId = extractUserId(authHeader);
        if (userId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("유효하지 않은 토큰입니다."));
        }

        waitingQueueService.remove(userId);
        return ResponseEntity.ok(ApiResponse.success("대기열에서 취소되었습니다.", null));
    }

    /**
     * Authorization 헤더에서 userId 추출
     * "Bearer eyJhbGci..." → userId
     */
    private String extractUserId(String authHeader) {
        // "Bearer " 접두사 제거 후 토큰만 꺼냄
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }

        return jwtTokenProvider.getUsernameFromJWT(token);
    }
}