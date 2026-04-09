package com.marketlens.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 대기열 서비스 (운영 환경 전용)
 *
 * Redis 구조:
 *   waiting-queue  : Sorted Set, member=sessionId,  score=진입시각(epoch ms)
 *   active-users   : Sorted Set, member=entryToken, score=만료시각(epoch ms)
 *
 * 식별자 구간 분리:
 *   대기열 구간 → sessionId (JS 메모리에만 존재, 새로고침 시 소멸 = 순번 초기화)
 *   Secret  구간 → entryToken (localStorage에 저장, 자리 반납 식별자로 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final StringRedisTemplate stringRedisTemplate;

    @org.springframework.beans.factory.annotation.Value("${queue.capacity:2}")
    private long capacity;

    @org.springframework.beans.factory.annotation.Value("${queue.active-ttl-minutes:10}")
    private long activeTtlMinutes;

    private static final String QUEUE_KEY        = "waiting-queue";
    private static final String ACTIVE_USERS_KEY = "active-users";

    /**
     * 서버 시작 시 대기열 상태 초기화
     */
    @PostConstruct
    public void clearQueueOnStartup() {
        stringRedisTemplate.delete(QUEUE_KEY);
        stringRedisTemplate.delete(ACTIVE_USERS_KEY);
        log.info("[대기열] 서버 시작 - 대기열/활성세션 초기화 완료");
    }

    /**
     * 대기열 진입
     *
     * @param sessionId 컨트롤러에서 UUID로 발급한 세션 ID
     */
    public void enter(String sessionId) {
        double score = Instant.now().toEpochMilli();
        stringRedisTemplate.opsForZSet().add(QUEUE_KEY, sessionId, score);
        log.info("[대기열] 진입 - sessionId={}, score={}", sessionId, score);
    }

    /**
     * 내 순번 조회 (0-based → +1 해서 반환)
     *
     * @param sessionId 조회할 세션 ID
     * @return 순번 (1부터 시작), 대기열에 없으면 -1
     */
    public long getRank(String sessionId) {
        Long rank = stringRedisTemplate.opsForZSet().rank(QUEUE_KEY, sessionId);
        if (rank == null) {
            log.debug("[대기열] 순번 조회 실패 - sessionId={} (대기열에 없음)", sessionId);
            return -1;
        }
        return rank + 1;
    }

    /**
     * 현재 대기열 전체 인원 수
     */
    public long getSize() {
        Long size = stringRedisTemplate.opsForZSet().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * 앞에서 N개 세션 꺼내기 (스케줄러에서 호출)
     *
     * ZPOPMIN: 조회 + 삭제를 원자적으로 처리
     * → 서버 여러 대가 동시에 실행해도 같은 세션이 중복으로 꺼내지지 않음
     */
    public Set<String> popFront(long count) {
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().popMin(QUEUE_KEY, count);

        if (tuples == null || tuples.isEmpty()) {
            return Set.of();
        }

        Set<String> sessions = new java.util.HashSet<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null) {
                sessions.add(tuple.getValue());
            }
        }

        log.info("[대기열] {}개 세션 입장 허용 - {}", sessions.size(), sessions);
        return sessions;
    }

    /**
     * 대기열 전체 세션 조회 (스케줄러에서 순번 push 용도)
     */
    public Set<String> getAllSessions() {
        Set<String> sessions = stringRedisTemplate.opsForZSet().range(QUEUE_KEY, 0, -1);
        return sessions != null ? sessions : Set.of();
    }

    /**
     * 특정 세션을 대기열에서 제거
     */
    public void remove(String sessionId) {
        stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, sessionId);
        log.info("[대기열] 제거 - sessionId={}", sessionId);
    }

    // ─── 정원 관리 ──────────────────────────────────────────

    /**
     * 입장 확정된 세션을 active-users에 추가 (entryToken 기준)
     *
     * score = 만료시각(epoch ms) → 스케줄러가 만료된 멤버를 주기적으로 강제 퇴장
     *
     * @param entryToken 입장 토큰 (Secret 페이지 구간 식별자)
     */
    public void enterActive(String entryToken) {
        double expireAt = Instant.now().toEpochMilli() + Duration.ofMinutes(activeTtlMinutes).toMillis();
        stringRedisTemplate.opsForZSet().add(ACTIVE_USERS_KEY, entryToken, expireAt);
        log.info("[정원] 입장 - entryToken={}, 만료시각={}, 현재 입장 인원={}", entryToken, expireAt, getActiveCount());
    }

    /**
     * Secret 페이지 퇴장 (entryToken 기준으로 active-users에서 제거)
     *
     * @param entryToken 입장 토큰
     */
    public void leaveActiveByToken(String entryToken) {
        stringRedisTemplate.opsForZSet().remove(ACTIVE_USERS_KEY, entryToken);
        log.info("[정원] 퇴장 - entryToken={}, 남은 입장 인원={}", entryToken, getActiveCount());
    }

    /**
     * 만료된 입장 세션 강제 퇴장 처리 (스케줄러에서 주기적으로 호출)
     *
     * score(만료시각) < 현재 시각인 멤버를 일괄 제거
     */
    public Set<String> evictExpiredActiveUsers() {
        long now = Instant.now().toEpochMilli();

        Set<String> expired = stringRedisTemplate.opsForZSet()
                .rangeByScore(ACTIVE_USERS_KEY, 0, now);

        if (expired == null || expired.isEmpty()) {
            return Set.of();
        }

        for (String entryToken : expired) {
            stringRedisTemplate.opsForZSet().remove(ACTIVE_USERS_KEY, entryToken);
            log.warn("[정원] TTL 만료 강제 퇴장 - entryToken={}", entryToken);
        }

        return expired;
    }

    /**
     * 현재 입장 중인 세션 수 (만료되지 않은 세션만 카운트)
     */
    public long getActiveCount() {
        long now = Instant.now().toEpochMilli();
        Long count = stringRedisTemplate.opsForZSet()
                .count(ACTIVE_USERS_KEY, now, Double.MAX_VALUE);
        return count != null ? count : 0;
    }

    /**
     * 빈 자리 수 = 정원 - 현재 입장 인원
     */
    public long getAvailableSlots() {
        return Math.max(0, capacity - getActiveCount());
    }

    /**
     * 설정된 정원 반환
     */
    public long getCapacity() {
        return capacity;
    }
}