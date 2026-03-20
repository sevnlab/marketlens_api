package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 대기열 스케줄러 (운영/테스트 환경 전용)
 *
 * 주기적으로 대기열 앞 N명을 꺼내 Kafka 에 publish
 * Kafka Consumer 가 설정된 속도로 DB 처리
 *
 * 흐름:
 *   Redis 대기열 → (5초마다) → 앞 10명 pop → Kafka publish → DB 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"real", "test"})
public class WaitingQueueScheduler {

    private final WaitingQueueService waitingQueueService;
    private final KafkaProducerService kafkaProducerService;
    private final SseEmitterService sseEmitterService;

    // 한 번에 입장 허용할 인원 수
    private static final long BATCH_SIZE = 10;

    /**
     * 5초마다 실행
     * 대기열 앞 10명을 꺼내서 Kafka 에 publish
     *
     * fixedDelay  = 이전 실행 완료 후 5초 뒤에 다시 실행 (겹치지 않음)
     * initialDelay = 서버 시작 후 10초 뒤에 첫 실행 (서버 초기화 대기)
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processQueue() {
        long total = waitingQueueService.getSize();

        if (total == 0) {
            return; // 대기열 비어있으면 아무것도 안 함
        }

        log.info("[스케줄러] 대기열 처리 시작 - 현재 대기 인원: {}명", total);

        // 앞 N명 꺼내기 (Redis에서 pop → 대기열에서 제거됨)
        Set<String> users = waitingQueueService.popFront(BATCH_SIZE);

        // 꺼낸 유저들을 Kafka 에 publish
        for (String userId : users) {
            kafkaProducerService.publishApprovedUser(userId);
            log.info("[스케줄러] 입장 허용 → Kafka publish - userId={}", userId);
        }

        log.info("[스케줄러] 이번 배치 처리 완료 - {}명 publish", users.size());

        // 아직 대기 중인 유저들에게 변경된 순번 push
        // (입장 허용된 N명이 빠졌으므로 남은 유저들의 순번이 앞으로 당겨짐)
        waitingQueueService.getAllUsers().forEach(userId -> {
            long rank = waitingQueueService.getRank(userId);
            long remaining = waitingQueueService.getSize();
            sseEmitterService.sendRank(userId, rank, remaining);
        });
    }
}