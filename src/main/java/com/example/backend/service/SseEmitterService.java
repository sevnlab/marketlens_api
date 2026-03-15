package com.example.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 관리 서비스
 *
 * 유저별 SseEmitter를 ConcurrentHashMap으로 관리.
 * (ConcurrentHashMap: 여러 스레드가 동시에 접근해도 안전한 Map)
 *
 * 이벤트 종류:
 *   - connected : 연결 확인
 *   - rank      : 현재 순번 (대기 중 주기적으로 전송)
 *   - admitted  : 입장 허용 + 입장 토큰
 */
@Slf4j
@Service
public class SseEmitterService {

    // SSE 연결 타임아웃: 30분
    // 타임아웃이 지나면 클라이언트에서 자동으로 재연결 시도함
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    // userId → SseEmitter 보관
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성
     * 클라이언트가 GET /api/queue/stream 요청 시 호출됨
     */
    public SseEmitter connect(String userId) {
        // 기존 연결이 있으면 먼저 닫기 (중복 연결 방지)
        SseEmitter existing = emitters.remove(userId);
        if (existing != null) {
            existing.complete();
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // 연결 종료 / 타임아웃 / 에러 → Map에서 제거
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        emitters.put(userId, emitter);

        // 연결 직후 확인 이벤트 전송 (브라우저가 연결됐는지 확인용)
        send(userId, "connected", Map.of("message", "대기열 연결 완료"));

        log.info("[SSE] 연결 - userId={}, 현재 연결 수={}", userId, emitters.size());
        return emitter;
    }

    /**
     * 순번 정보 전송 (스케줄러에서 주기적으로 호출)
     *
     * @param userId 전송 대상
     * @param rank   현재 순번
     * @param total  전체 대기 인원
     */
    public void sendRank(String userId, long rank, long total) {
        send(userId, "rank", Map.of("rank", rank, "total", total));
    }

    /**
     * 입장 허용 알림 전송 (KafkaConsumerService에서 호출)
     * 전송 후 연결 종료 (더 이상 대기열에 없으므로)
     *
     * @param userId     입장 허용 대상
     * @param entryToken 입장 토큰 (이후 실제 서비스 접근에 사용)
     */
    public void sendAdmitted(String userId, String entryToken) {
        send(userId, "admitted", Map.of("entryToken", entryToken, "message", "입장이 허용되었습니다."));

        // 입장 허용 후 연결 종료
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    /**
     * SSE 연결 여부 확인
     */
    public boolean isConnected(String userId) {
        return emitters.containsKey(userId);
    }

    /**
     * 실제 이벤트 전송 내부 메서드
     * 전송 실패(클라이언트 끊김 등) 시 emitter 제거
     */
    private void send(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            // 클라이언트가 연결을 끊은 경우 등
            log.warn("[SSE] 전송 실패 - userId={}, event={}", userId, eventName);
            emitters.remove(userId);
        }
    }
}