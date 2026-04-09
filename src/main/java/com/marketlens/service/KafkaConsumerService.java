package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka Consumer 서비스
 *
 * KafkaProducerService 가 발행한 입장 허용 세션을 수신하여 처리
 * concurrency = 3 (KafkaConfig 설정과 동일) → 파티션 3개를 병렬 처리
 *
 * 처리 흐름:
 *   1. Kafka 메시지 수신 (sessionId)
 *   2. 입장 토큰 발급 & Redis 저장
 *   3. Redis Pub/Sub "queue:admitted" 채널에 발행
 *      → 모든 서버 인스턴스의 RedisSubscriberService.onMessage() 호출
 *      → emitter 를 보유한 인스턴스가 SSE 전송
 *   4. acknowledgment.acknowledge() 로 수동 commit
 *
 * ─── SSE 직접 호출 → Redis Pub/Sub 변경 이유 ──────────────────────────
 * 기존: processUserEntry() 에서 sseEmitterService.sendAdmitted() 직접 호출
 *
 * 문제: Kafka Consumer 는 그룹 내 파티션을 분담.
 *       서버 인스턴스가 여러 대(로컬 + VM)면 파티션이 각 인스턴스에 분배됨.
 *       브라우저가 연결된 서버와 Kafka Consumer 가 실행 중인 서버가 다를 경우,
 *       Consumer 서버에는 해당 sessionId 의 emitter 가 없어 SSE 전송 불가.
 *
 *       실제 발생한 장애:
 *         - 로컬(localhost:7777): 브라우저 SSE 연결 → emitter 존재
 *         - VM(192.168.87.138): partition-0 담당 → 메시지 수신 후 sendAdmitted() 호출
 *         - VM emitter 맵: sessionId 없음 → SSE 전송 실패 → 사용자 영원히 대기
 *
 * 해결: Kafka Consumer 는 입장 처리(토큰 발급/정원 등록)만 수행.
 *       SSE 알림은 Redis Pub/Sub 을 통해 전체 인스턴스에 브로드캐스트.
 *       emitter 를 가진 인스턴스만 실제 SSE 전송 (RedisSubscriberService 참고)
 * ──────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final WaitingQueueService waitingQueueService;
    private final StringRedisTemplate stringRedisTemplate;

    // MSA 구조
//    // 배송 서버
//    @KafkaListener(groupId = "delivery-consumer", topics = "주문완료")
//    public void handleDelivery(String orderId) {
//        // 배송 처리 로직
//    }
//
//    // 정산 서버
//    @KafkaListener(groupId = "payment-consumer", topics = "주문완료")
//    public void handlePayment(String orderId) {
//        // 정산 처리 로직
//    }

    @KafkaListener(
            topics = "${kafka.topic.waiting-queue}", // 이 토픽으로 발행시 자동 실행
            groupId = "${spring.kafka.consumer.group-id}", // 같은 그룹ID면 파티션을 나눠서 처리, 다르게 구성하면 하나의 토픽이 발행되었을때 각각 따로 처리 가능(배송, 정산, 알림 등등), yml 에서 다르게 구성후 배포
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeApprovedUser(
            @Payload String sessionId,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, // 몇번 파티션에서왔는지
            @Header(KafkaHeaders.OFFSET) long offset, // 파티션 내 위치, 문제 발생 시 파티션2, 오프셋150번 메시지에서 터졌다고 에러 추적가능
            Acknowledgment acknowledgment)
    {
        log.info("[Kafka] 메시지 수신 - sessionId={}, partition={}, offset={}", sessionId, partition, offset);

        try {
            processSessionEntry(sessionId);
            acknowledgment.acknowledge();
            log.info("[Kafka] 처리 완료 - sessionId={}", sessionId);

        } catch (Exception e) {
            // commit 하지 않음 → Kafka가 해당 메시지를 재전달
            log.error("[Kafka] 처리 실패 - sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 입장 토큰 발급 후 Redis Pub/Sub 으로 입장 허용 이벤트 발행
     *
     * TODO: DB 접수 처리 로직 추가 예정
     *
     * SSE 직접 호출을 제거하고 Redis Pub/Sub 으로 전환한 이유:
     *   이 메서드는 Kafka Consumer 스레드에서 실행되며, 어느 서버 인스턴스가
     *   이 파티션을 담당하는지는 Kafka 가 결정함.
     *   브라우저가 연결된 서버와 다른 인스턴스일 수 있으므로 SSE 를 직접 호출하면
     *   emitter 를 찾지 못해 전송 실패. Redis Pub/Sub 으로 전체 인스턴스에 알림.
     *   (자세한 내용은 클래스 주석 참고)
     */
    private void processSessionEntry(String sessionId) {
        log.debug("[Kafka] 입장 처리 시작 - sessionId={}", sessionId);

        // TODO: DB에 실제 접수 처리

        // 입장 토큰 발급 (UUID 기반)
        // - Secret 페이지 진입 가드 역할 (대기열 미통과자의 URL 직접 접근 차단)
        // - active-users에 entryToken 기준으로 저장 → Secret 페이지에서 자리 반납 시 식별자로 사용
        String entryToken = java.util.UUID.randomUUID().toString();

        // 정원 세트에 entryToken 기준으로 추가
        // Secret 페이지에는 sessionId가 없고 entryToken만 있으므로 entryToken으로 식별
        waitingQueueService.enterActive(entryToken);

        // Redis Pub/Sub "queue:admitted" 채널에 발행
        // 포맷: "sessionId:entryToken"
        // → 모든 서버 인스턴스의 RedisSubscriberService.onMessage() 가 호출됨
        // → emitter 를 보유한 인스턴스가 SSE 전송
        String pubSubMessage = sessionId + ":" + entryToken;
        stringRedisTemplate.convertAndSend(RedisSubscriberService.ADMITTED_CHANNEL, pubSubMessage);
        log.info("[Kafka] 입장 처리 완료 → Redis Pub/Sub 발행 - sessionId={}", sessionId);
    }
}