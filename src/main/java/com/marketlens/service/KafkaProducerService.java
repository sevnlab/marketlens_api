package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer 서비스 (운영/테스트 환경 전용)
 *
 * 대기열 스케줄러가 주기적으로 앞 N명을 뽑아 이 서비스를 통해 Kafka에 publish
 * Kafka Consumer(KafkaConsumerService)가 메시지를 받아 DB 처리를 수행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.waiting-queue}")
    private String waitingQueueTopic;

    /**
     * 입장 허용된 유저를 Kafka 토픽에 발행
     *
     * @param sessionId 입장 허용할 유저 ID
     *
     * key = userId 로 설정 → 같은 유저의 메시지는 항상 같은 파티션으로 전달
     * (파티션 내 순서 보장 + 중복 처리 방지에 유리)
     */
    public void publishApprovedUser(String sessionId) {

        // 카프카에서 send() 는 비동기라 결과를 기다리지 않고 바로 리턴, 나중에 성공/실패 결과가 future 에 담김
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(waitingQueueTopic, sessionId, sessionId);

        future.whenComplete((result, ex) -> {
            if (ex != null) {

                // 대기열에 다시넣는 로직 필요 (일단 주석해놓고 추후 고민)
                // 방법 1: 대기열에 다시 넣기
                // waitingQueueService.enter(userId);

                // 방법 2: Dead Letter Queue (실패 전용 토픽)에 따로 보관
                // kafkaTemplate.send("waiting-queue-failed", userId, userId);
                log.error("[Kafka] 메시지 발행 실패 - sessionId={}, error={}", sessionId, ex.getMessage());
            } else {
                log.debug("[Kafka] 메시지 발행 성공 - sessionId={}, partition={}, offset={}",
                        sessionId,
                        result.getRecordMetadata().partition(), // 몇번 파티션에 들어갔는지
                        result.getRecordMetadata().offset()); // 그 파티션에서 몇번째 메시지인지
            }
        });
    }
}
