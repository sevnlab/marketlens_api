package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka Consumer 서비스 (운영/테스트 환경 전용)
 *
 * KafkaProducerService 가 발행한 입장 허용 유저를 수신하여 DB 처리
 * concurrency = 3 (KafkaConfig 설정과 동일) → 파티션 3개를 병렬 처리
 *
 * 처리 흐름:
 *   1. Kafka 메시지 수신 (userId)
 *   2. DB에 실제 접수/예약 처리
 *   3. 처리 완료 → 입장 토큰 발급 & Redis에 저장
 *   4. SSE 로 유저에게 "입장 가능" 알림
 *   5. acknowledgment.acknowledge() 로 수동 commit
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile({"real", "test"})
public class KafkaConsumerService {

    private final SseEmitterService sseEmitterService;
    private final WaitingQueueService waitingQueueService;

    @KafkaListener(
            topics = "${kafka.topic.waiting-queue}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeApprovedUser(
            @Payload String userId,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("[Kafka] 메시지 수신 - userId={}, partition={}, offset={}", userId, partition, offset);

        try {
            processUserEntry(userId);
            acknowledgment.acknowledge(); // 처리 성공 시에만 commit
            log.info("[Kafka] 처리 완료 - userId={}", userId);

        } catch (Exception e) {
            // commit 하지 않음 → Kafka가 해당 메시지를 재전달
            log.error("[Kafka] 처리 실패 - userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 실제 DB 처리 및 입장 토큰 발급
     *
     * TODO: DB 접수 처리 로직 추가 예정
     *
     * 현재:
     *   - 입장 토큰 발급 (UUID)
     *   - SSE로 클라이언트에 입장 허용 알림
     */
    private void processUserEntry(String userId) {
        log.debug("[Kafka] DB 처리 시작 - userId={}", userId);

        // TODO: DB에 실제 접수 처리

        // 입장 토큰 발급 (UUID 기반)
        String entryToken = java.util.UUID.randomUUID().toString();

        // Redis에 입장 토큰 저장 (TTL 5분)
        waitingQueueService.saveEntryToken(userId, entryToken);

        // 정원 세트에 추가 (퇴장 시 leaveActive 호출로 자리 반납)
        waitingQueueService.enterActive(userId);

        // SSE 연결 중인 유저에게 입장 허용 알림
        if (sseEmitterService.isConnected(userId)) {
            sseEmitterService.sendAdmitted(userId, entryToken);
            log.info("[Kafka] 입장 허용 SSE 전송 - userId={}", userId);
        } else {
            // SSE 미연결 상태 (브라우저 닫힘 등) → Redis에 토큰 저장됨, 5분 내 재접속 시 조회 가능
            log.warn("[Kafka] SSE 미연결 - userId={}, 토큰 Redis 저장 완료 (TTL 5min)", userId);
        }
    }
}
