package com.marketlens.controller;

import com.marketlens.dto.ApiResponse;
import com.marketlens.service.SseEmitterService;
import com.marketlens.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * 대기열 컨트롤러 (운영 환경 전용)
 *
 * 대기열은 sessionId 단위로 관리됨.
 * - 진입할 때마다 새 sessionId 발급 → 다른 기기/탭은 항상 맨 뒤에서 시작
 * - 새로고침(F5) = sessionId 소멸 = 순번 초기화 (실제 티켓팅과 동일)
 * - 인증(로그인)은 JWT 쿠키로 확인, 대기열 추적은 sessionId로 처리
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queue")
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;
    private final SseEmitterService sseEmitterService;

    /**
     * 대기열 진입
     * 호출할 때마다 새 sessionId 발급 → 응답으로 클라이언트에 전달
     */
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<?>> enter(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        String sessionId = UUID.randomUUID().toString(); // 탭마다 공유 불가
        waitingQueueService.enter(sessionId);
        long rank = waitingQueueService.getRank(sessionId);
        long total = waitingQueueService.getSize();

        log.info("[대기열 진입] userId={}, sessionId={}, rank={}, total={}", authentication.getName(), sessionId, rank, total);

        return ResponseEntity.ok(ApiResponse.success("대기열에 등록되었습니다.",
                Map.of("sessionId", sessionId, "rank", rank, "total", total)));
    }

    /**
     * 내 순번 조회
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<?>> status(Authentication authentication,
                                                  @RequestParam String sessionId) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        long rank = waitingQueueService.getRank(sessionId);
        if (rank == -1) {
            return ResponseEntity.ok(ApiResponse.fail("대기열에 등록되지 않은 세션입니다."));
        }

        long total = waitingQueueService.getSize();
        return ResponseEntity.ok(ApiResponse.success("조회 성공",
                Map.of("rank", rank, "total", total)));
    }

    /**
     * SSE 구독 (실시간 순번/입장 알림)
     * EventSource는 커스텀 헤더 불가 → sessionId를 쿼리 파라미터로 받음
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication,
                             @RequestParam String sessionId) {
        if (authentication == null) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalArgumentException("로그인이 필요합니다."));
            return emitter;
        }

        log.info("[SSE] 구독 요청 - userId={}, sessionId={}", authentication.getName(), sessionId);
        return sseEmitterService.connect(sessionId);
    }

    /**
     * 대기열 취소
     */
    @DeleteMapping("/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(Authentication authentication,
                                                  @RequestParam String sessionId) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        waitingQueueService.remove(sessionId);
        return ResponseEntity.ok(ApiResponse.success("대기열에서 취소되었습니다.", null));
    }

    /**
     * 브라우저 종료 시 정리 (sendBeacon 호출용)
     * 대기열 구간: sessionId로 대기열 제거 + 활성 제거
     * Secret 구간: entryToken으로 활성 제거
     * sendBeacon: navigator.sendBeacon('/api/queue/exit?sessionId=xxx')
     *             navigator.sendBeacon('/api/queue/exit?entryToken=xxx')
     */
    @PostMapping("/exit")
    public ResponseEntity<ApiResponse<?>> exit(Authentication authentication,
                                               @RequestParam(required = false) String sessionId,
                                               @RequestParam(required = false) String entryToken) {
        if (authentication == null) {
            return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
        }

        if (sessionId != null) {
            // 대기열 구간에서 종료 (대기열에서만 제거, active-users는 아직 미등록 상태)
            waitingQueueService.remove(sessionId);
            log.info("[브라우저 종료] 대기열 구간 - userId={}, sessionId={}", authentication.getName(), sessionId);
        } else if (entryToken != null) {
            // Secret 구간에서 종료 (활성 제거만)
            waitingQueueService.leaveActiveByToken(entryToken);
            log.info("[브라우저 종료] Secret 구간 - userId={}, entryToken={}", authentication.getName(), entryToken);
        }

        return ResponseEntity.ok(ApiResponse.success("처리 완료", null));
    }

    /**
     * 퇴장 (자리 반납)
     * Secret 페이지에서 나갈 때 호출 → 빈 자리 생성 → 다음 대기자 자동 입장
     * entryToken으로 식별 (Secret 페이지에는 sessionId가 없음)
     */
    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<?>> leave(Authentication authentication,
                                                @RequestParam String entryToken) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("로그인이 필요합니다."));
        }

        waitingQueueService.leaveActiveByToken(entryToken);

        long activeCount = waitingQueueService.getActiveCount();
        long capacity    = waitingQueueService.getCapacity();
        log.info("[퇴장] userId={}, entryToken={}, 남은 입장 인원={}/{}", authentication.getName(), entryToken, activeCount, capacity);

        return ResponseEntity.ok(ApiResponse.success("퇴장 처리 완료", null));
    }
}