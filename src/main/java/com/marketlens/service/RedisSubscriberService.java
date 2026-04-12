package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub 구독 서비스
 *
 * ─── 도입 배경 (멀티 인스턴스 문제) ─────────────────────────────────────
 * 기존 방식: Kafka Consumer → sseEmitterService.sendAdmitted() 직접 호출
 *
 * 문제 상황:
 *   - SSE emitter 는 각 서버 인스턴스의 메모리(ConcurrentHashMap)에만 존재
 *   - 브라우저가 "로컬 서버(localhost:7777)"에 SSE 연결 → emitter 는 로컬 서버에 있음
 *   - Kafka Consumer 는 "VM 서버(192.168.87.138)"에서 파티션을 할당받아 실행
 *   - VM 서버가 메시지 수신 후 sendAdmitted() 호출 → VM 서버 emitter 맵에 해당 userId 없음
 *   - → SSE 전송 실패 → 사용자가 대기 화면에서 영원히 빠져나가지 못함
 *
 * 실제 발생한 증상:
 *   Kafka 발행 성공 로그는 찍히지만 "[Kafka] 메시지 수신" 로그가 로컬에 안 보임
 *   → VM 서버가 메시지를 소비하고 있었기 때문
 *   (kafka-consumer-groups.sh 확인 결과 /192.168.87.138 이 partition-0 담당)
 *
 * 해결 방식:
 *   Kafka Consumer → Redis "queue:admitted" 채널에 "userId:entryToken" 발행
 *   모든 서버 인스턴스가 해당 채널 구독 (RedisConfiguration 의 리스너 컨테이너)
 *   → onMessage() 호출 시 이 인스턴스의 emitter 맵 확인
 *   → emitter 있으면 SSE 전송, 없으면 조용히 무시 (다른 인스턴스가 처리)
 * ──────────────────────────────────────────────────────────────────────
 *
 * 메시지 포맷: "sessionId:entryToken"
 *   예) "550e8400-e29b-41d4-a716-446655440000:a1b2c3d4-..."
 *   split(":", 2) 로 파싱 → UUID 내부의 "-" 는 영향 없음, ":" 는 UUID 에 포함되지 않음
 */
@Slf4j
@Service
@Profile({"real", "dev", "test"})
@RequiredArgsConstructor
public class RedisSubscriberService implements MessageListener {

    /** Redis Pub/Sub 채널 이름 (KafkaConsumerService, RedisConfiguration 에서 공통 참조) */
    public static final String ADMITTED_CHANNEL = "queue:admitted";

    private final SseEmitterService sseEmitterService;

    /**
     * Redis "queue:admitted" 채널 메시지 수신 시 호출
     *
     * 모든 서버 인스턴스에서 동시에 호출되지만,
     * 실제 SSE 전송은 emitter 를 보유한 인스턴스에서만 이루어짐.
     *
     * @param message Redis 메시지 ("sessionId:entryToken" 형식)
     * @param pattern 구독 패턴 (ChannelTopic 이므로 null)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {

        // Redis 메시지 본문을 UTF-8 문자열로 변환
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        // "sessionId:entryToken" 파싱 (최대 2분할 → entryToken 내부의 "-" 는 유지)
        String[] parts = body.split(":", 2);
        if (parts.length != 2) {
            log.warn("[Redis Pub/Sub] 잘못된 메시지 형식 - body={}", body);
            return;
        }

        String sessionId  = parts[0];
        String entryToken = parts[1];

        log.debug("[Redis Pub/Sub] 입장 허용 이벤트 수신 - sessionId={}", sessionId);

        // 이 서버 인스턴스에 해당 sessionId 의 SSE 연결이 있는지 확인
        if (sseEmitterService.isConnected(sessionId)) {
            // 이 인스턴스가 emitter 를 보유 → SSE 전송
            sseEmitterService.sendAdmitted(sessionId, entryToken);
            log.info("[Redis Pub/Sub] 입장 허용 SSE 전송 완료 - sessionId={}", sessionId);
        } else {
            // 이 인스턴스에는 emitter 없음 → 다른 인스턴스가 처리
            log.debug("[Redis Pub/Sub] 이 인스턴스에 SSE 연결 없음 - sessionId={}", sessionId);
        }
    }
}
