package com.marketlens.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE Emitter 관리 서비스
 *
 * sessionId별 SseEmitter를 ConcurrentHashMap으로 관리.
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

    // sessionId → SseEmitter 보관
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성
     * 클라이언트가 GET /api/queue/stream?sessionId=xxx 요청 시 호출됨
     */
    public SseEmitter connect(String sessionId) {
        // 기존 연결이 있으면 먼저 닫기 (같은 sessionId로 중복 연결 방지)
        // 현재 구조에서는 같은 sessionId로 들어올일이 없어 필요없는 코드
        // 네트워크 순간끊김으로 인해 브라우저 SSE 자동 재연결(retry) 할때 같은 sessionId로 재호출 하므로 SSE 자동 재연결 타이밍 이슈 방어용
        // remove()로 꺼내면서 동시에 삭제 → 다른 스레드가 sendRank() 호출해도 안전
        SseEmitter existing = emitters.remove(sessionId);
        if (existing != null) {
            existing.complete();
            log.info("[SSE] 기존 연결 종료 - sessionId={}", sessionId);
        }

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // 연결 종료 / 타임아웃 / 에러 → Map에서 제거
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        emitters.put(sessionId, emitter);

        // 연결 직후 확인 이벤트 전송
        send(sessionId, "connected", Map.of("message", "대기열 연결 완료"));

        log.info("[SSE] 연결 - sessionId={}, 현재 연결 수={}", sessionId, emitters.size());
        return emitter;
    }

    /**
     * 순번 정보 전송 (스케줄러에서 주기적으로 호출)
     *
     * @param sessionId 전송 대상
     * @param rank      현재 순번
     * @param total     전체 대기 인원
     */
    public void sendRank(String sessionId, long rank, long total) {
        send(sessionId, "rank", Map.of("rank", rank, "total", total));
    }

    /**
     * 입장 허용 알림 전송 (RedisSubscriberService에서 호출)
     * 전송 후 연결 종료 (더 이상 대기열에 없으므로)
     *
     * @param sessionId  입장 허용 대상
     * @param entryToken 입장 토큰 (이후 실제 서비스 접근에 사용)
     */
    public void sendAdmitted(String sessionId, String entryToken) {
        send(sessionId, "admitted", Map.of("entryToken", entryToken, "message", "입장이 허용되었습니다."));

        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    /**
     * SSE 연결 여부 확인
     */
    public boolean isConnected(String sessionId) {
        return emitters.containsKey(sessionId);
    }

    /**
     * 실제 이벤트 전송 내부 메서드
     * 전송 실패(클라이언트 끊김 등) 시 emitter 제거
     */
    private void send(String sessionId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return;
        }

        try {
            // MediaType.APPLICATION_JSON 명시 필수
            // 생략하면 Map이 {rank=1, total=1} 형태 문자열로 직렬화돼서
            // 프론트의 JSON.parse() 가 실패함
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("[SSE] 전송 실패 - sessionId={}, event={}", sessionId, eventName);
            emitters.remove(sessionId);
        }
    }
}