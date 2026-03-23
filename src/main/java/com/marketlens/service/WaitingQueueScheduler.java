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

    /**
     * 3초마다 실행
     *
     * 실행 순서:
     *   1. TTL 만료된 입장 유저 강제 퇴장 (브라우저 강제 종료 등 대비)
     *   2. 빈 자리 계산 후 대기열 앞 유저를 Kafka 로 publish
     *   3. 남은 대기자들에게 변경된 순번 SSE push
     *
     * fixedDelay  = 이전 실행 완료 후 3초 뒤 재실행 (동시 실행 방지)
     * initialDelay = 서버 시작 후 10초 뒤 첫 실행 (초기화 대기)
     */
    @Scheduled(fixedDelay = 3000, initialDelay = 10000)
    public void processQueue() {

        // ── Step 1. TTL 만료 유저 강제 퇴장 ──────────────────
        // 브라우저 강제 종료 등으로 /leave API를 못 호출한 유저를 자동 정리
        // WaitingQueueService.evictExpiredActiveUsers() 가 Redis Sorted Set에서
        // score(만료시각) < 현재 시각인 멤버를 일괄 제거
        Set<String> evicted = waitingQueueService.evictExpiredActiveUsers();
        if (!evicted.isEmpty()) {
            log.info("[스케줄러] TTL 만료 강제 퇴장 {}명 → 자리 반납 완료", evicted.size());
        }

        // ── Step 2. 빈 자리만큼 대기열 입장 처리 ─────────────
        long available = waitingQueueService.getAvailableSlots(); // 정원 - 현재 입장 인원
        long total     = waitingQueueService.getSize();           // 대기 중인 인원

        if (available > 0 && total > 0) {
            log.info("[스케줄러] 빈 자리={}, 대기 인원={} → 입장 처리 시작", available, total);

            // Redis 대기열 앞에서 available 명 꺼내기 (꺼낸 즉시 대기열에서 제거됨)
            Set<String> users = waitingQueueService.popFront(available);

            // 꺼낸 유저들을 Kafka 토픽에 발행 → KafkaConsumerService 가 처리 후 SSE 전송
            for (String userId : users) {
                kafkaProducerService.publishApprovedUser(userId);
                log.info("[스케줄러] 입장 허용 → Kafka publish - userId={}", userId);
            }
        }

        // ── Step 3. 대기자 순번 업데이트 (SSE push) ──────────
        // 빈 자리 유무와 관계없이 항상 실행
        // → 다른 유저가 나중에 대기열에 합류했을 때 기존 대기자들의 total도 최신화됨
        long remaining = waitingQueueService.getSize();
        if (remaining > 0) {
            waitingQueueService.getAllUsers().forEach(userId -> {
                long rank = waitingQueueService.getRank(userId);
                sseEmitterService.sendRank(userId, rank, remaining);
            });
        }
    }
}