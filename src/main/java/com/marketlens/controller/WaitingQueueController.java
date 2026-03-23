package com.marketlens.controller;

import com.marketlens.dto.ApiResponse;
import com.marketlens.service.SseEmitterService;
import com.marketlens.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 대기열 컨트롤러 (운영 환경 전용)
 *
 * JwtAuthenticationFilter 가 쿠키에서 JWT를 읽어 SecurityContext에 등록하므로
 * 컨트롤러에서는 Authentication 파라미터로 로그인한 사용자 정보를 바로 받음.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queue")
@Profile({"real", "test"})
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;
    private final SseEmitterService sseEmitterService;

    /**
     * 대기열 진입
     */
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<?>> enter(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        String userId = authentication.getName();
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
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<?>> status(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        String userId = authentication.getName();
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
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        if (authentication == null) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalArgumentException("로그인이 필요합니다."));
            return emitter;
        }

        String userId = authentication.getName();
        log.info("[SSE] 구독 요청 - userId={}", userId);
        return sseEmitterService.connect(userId);
    }

    /**
     * 대기열 취소
     */
    @DeleteMapping("/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        waitingQueueService.remove(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("대기열에서 취소되었습니다.", null));
    }

    /**
     * 퇴장 (자리 반납)
     * Secret 페이지에서 나갈 때 호출 → 빈 자리 생성 → 다음 대기자 자동 입장
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<?>> leave(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        String userId = authentication.getName();
        waitingQueueService.leaveActive(userId);

        long activeCount = waitingQueueService.getActiveCount();
        long capacity    = waitingQueueService.getCapacity();
        log.info("[퇴장] userId={}, 남은 입장 인원={}/{}", userId, activeCount, capacity);

        return ResponseEntity.ok(ApiResponse.success("퇴장 처리 완료", null));
    }

    /**
     * 입장 토큰 조회 (SSE 미연결 유저 재접속 시)
     */
    @GetMapping("/token")
    public ResponseEntity<ApiResponse<?>> getToken(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        String entryToken = waitingQueueService.getEntryToken(authentication.getName());
        if (entryToken == null) {
            return ResponseEntity.ok(ApiResponse.fail("입장 토큰이 없습니다. (만료되었거나 아직 입장 허용 전)"));
        }

        return ResponseEntity.ok(ApiResponse.success("입장 토큰 조회 성공",
                Map.of("entryToken", entryToken)));
    }
}