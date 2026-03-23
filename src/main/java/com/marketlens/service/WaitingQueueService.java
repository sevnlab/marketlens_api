package com.marketlens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 대기열 서비스 (운영 환경 전용)
 *
 * Redis Sorted Set 구조:
 *   key   : "waiting-queue"
 *   member: userId
 *   score : 대기열 진입 시각 (epoch milliseconds) → 먼저 들어온 사람이 낮은 score = 앞 순번
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile({"real", "test"})
public class WaitingQueueService {

    private final StringRedisTemplate stringRedisTemplate;

    // ─── 설정값 (@Value → application.yml 에서 주입) ──────────

    /** 동시 입장 허용 최대 인원 */
    @org.springframework.beans.factory.annotation.Value("${queue.capacity:2}")
    private long capacity;

    /**
     * 입장 후 최대 체류 시간 (분)
     * 이 시간이 지나면 브라우저가 꺼져 있어도 강제 퇴장 처리됨
     */
    @org.springframework.beans.factory.annotation.Value("${queue.active-ttl-minutes:10}")
    private long activeTtlMinutes;

    // ─── Redis Key 상수 ─────────────────────────────────────

    /** 대기열 Sorted Set: member=userId, score=진입시각(epoch ms) */
    private static final String QUEUE_KEY = "waiting-queue";

    /**
     * 입장 중 유저 Sorted Set: member=userId, score=만료시각(epoch ms)
     *
     * ※ Redis Set은 개별 멤버에 TTL을 걸 수 없어서 Sorted Set으로 관리
     *   score(만료시각)를 기준으로 스케줄러가 만료된 유저를 주기적으로 제거
     */
    private static final String ACTIVE_USERS_KEY = "active-users";

    /** 입장 토큰 키 접두사: entry-token:{userId} */
    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    // 입장 토큰 만료 시간: 5분 (재접속 여유 시간)
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    /**
     * 대기열 진입
     * 이미 대기열에 있으면 중복 등록하지 않음
     *
     * @param userId 대기열에 등록할 유저 ID
     * @return true: 신규 등록 / false: 이미 등록된 유저
     */
    public boolean enter(String userId) {
        // score = 현재 시각 (밀리초) → 먼저 들어온 사람이 작은 score = 앞 순번
        double score = Instant.now().toEpochMilli();

        // add() : 이미 존재하는 member면 false 반환 (중복 등록 방지)
        Boolean added = stringRedisTemplate.opsForZSet().add(QUEUE_KEY, userId, score);

        if (Boolean.TRUE.equals(added)) {
            log.info("[대기열] 진입 - userId={}, score={}", userId, score);
        } else {
            log.debug("[대기열] 이미 존재 - userId={}", userId);
        }

        return Boolean.TRUE.equals(added);
    }

    /**
     * 내 순번 조회 (0-based → +1 해서 반환)
     * 예) 0번째 = 1번 대기
     *
     * @param userId 조회할 유저 ID
     * @return 순번 (1부터 시작), 대기열에 없으면 -1
     */
    public long getRank(String userId) {
        Long rank = stringRedisTemplate.opsForZSet().rank(QUEUE_KEY, userId);

        if (rank == null) {
            log.debug("[대기열] 순번 조회 실패 - userId={} (대기열에 없음)", userId);
            return -1;
        }

        return rank + 1; // 0-based → 1-based
    }

    /**
     * 현재 대기열 전체 인원 수
     */
    public long getSize() {
        Long size = stringRedisTemplate.opsForZSet().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    /**
     * 앞에서 N명 꺼내기 (스케줄러에서 호출)
     * Kafka에 publish 할 대상 목록 반환
     *
     * @param count 꺼낼 인원 수
     * @return userId Set
     */
    public Set<String> popFront(long count) {
        // score 낮은 순(먼저 들어온 순)으로 0 ~ count-1 번째 조회
        Set<String> users = stringRedisTemplate.opsForZSet().range(QUEUE_KEY, 0, count - 1);

        if (users != null && !users.isEmpty()) {
            // 꺼낸 유저들을 대기열에서 제거
            stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, users.toArray());
            log.info("[대기열] {}명 입장 허용 - {}", users.size(), users);
        }

        return users != null ? users : Set.of();
    }

    /**
     * 대기열 전체 유저 조회 (스케줄러에서 순번 push 용도)
     */
    public Set<String> getAllUsers() {
        Set<String> users = stringRedisTemplate.opsForZSet().range(QUEUE_KEY, 0, -1);
        return users != null ? users : Set.of();
    }

    /**
     * 특정 유저를 대기열에서 제거 (취소, 이탈 등)
     *
     * @param userId 제거할 유저 ID
     */
    public void remove(String userId) {
        stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, userId);
        log.info("[대기열] 제거 - userId={}", userId);
    }

    /**
     * 입장 토큰 Redis 저장 (TTL 5분)
     * SSE 미연결 유저가 재접속했을 때 GET /api/queue/token 으로 조회 가능
     *
     * @param userId 유저 ID
     * @param token  입장 토큰 (UUID)
     */
    public void saveEntryToken(String userId, String token) {
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + userId, token, TOKEN_TTL);
        log.info("[대기열] 입장 토큰 저장 - userId={}, ttl=5min", userId);
    }

    /**
     * 입장 토큰 조회
     * 만료됐거나 아직 입장 허용 전이면 null 반환
     */
    public String getEntryToken(String userId) {
        return stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
    }

    // ─── 정원 관리 (Sorted Set 기반 TTL 자동 만료) ──────────

    /**
     * 유저를 입장 중 Sorted Set에 추가
     *
     * score = 현재 시각 + activeTtlMinutes(분) → 만료 시각(epoch ms)
     * 스케줄러가 score < 현재 시각인 멤버를 주기적으로 제거해 자동 퇴장 처리
     *
     * @param userId 입장 확정된 유저 ID
     */
    public void enterActive(String userId) {
        // 만료 시각 = 현재 시각 + TTL
        double expireAt = Instant.now().toEpochMilli() + Duration.ofMinutes(activeTtlMinutes).toMillis();
        stringRedisTemplate.opsForZSet().add(ACTIVE_USERS_KEY, userId, expireAt);
        log.info("[정원] 입장 - userId={}, 만료시각={}, 현재 입장 인원={}", userId, expireAt, getActiveCount());
    }

    /**
     * 유저를 입장 중 Sorted Set에서 제거 (명시적 퇴장)
     * 입장 토큰도 함께 삭제
     *
     * 호출 시점:
     *   - 사용자가 직접 나가기 버튼 클릭
     *   - 구매 완료
     *   - 10분 타이머 만료 (프론트엔드 호출)
     *   - beforeunload sendBeacon
     *
     * @param userId 퇴장할 유저 ID
     */
    public void leaveActive(String userId) {
        stringRedisTemplate.opsForZSet().remove(ACTIVE_USERS_KEY, userId);
        stringRedisTemplate.delete(TOKEN_KEY_PREFIX + userId);
        log.info("[정원] 퇴장 - userId={}, 남은 입장 인원={}", userId, getActiveCount());
    }

    /**
     * 만료된 입장 유저 강제 퇴장 처리 (스케줄러에서 주기적으로 호출)
     *
     * Redis Sorted Set의 score(만료시각) < 현재 시각인 멤버를 일괄 제거
     * → 브라우저 강제 종료 등으로 /leave API를 못 호출한 유저도 TTL 후 자동 퇴장됨
     *
     * @return 강제 퇴장된 유저 ID 목록 (로그 및 자리 반납 처리용)
     */
    public Set<String> evictExpiredActiveUsers() {
        long now = Instant.now().toEpochMilli();

        // score가 0 이상 ~ now 이하인 멤버 = 이미 만료된 유저
        Set<String> expired = stringRedisTemplate.opsForZSet()
                .rangeByScore(ACTIVE_USERS_KEY, 0, now);

        if (expired == null || expired.isEmpty()) {
            return Set.of();
        }

        // 만료된 유저 일괄 제거 + 입장 토큰도 함께 삭제
        for (String userId : expired) {
            stringRedisTemplate.opsForZSet().remove(ACTIVE_USERS_KEY, userId);
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + userId);
            log.warn("[정원] TTL 만료 강제 퇴장 - userId={}", userId);
        }

        return expired;
    }

    /**
     * 현재 입장 중인 유저 수 (만료되지 않은 유저만 카운트)
     *
     * ZCOUNT active-users {now} +inf
     * → score(만료시각) >= 현재 시각인 멤버만 집계
     */
    public long getActiveCount() {
        long now = Instant.now().toEpochMilli();
        // score가 now 이상 ~ +∞ 인 멤버 = 아직 만료되지 않은 유저
        Long count = stringRedisTemplate.opsForZSet()
                .count(ACTIVE_USERS_KEY, now, Double.MAX_VALUE);
        return count != null ? count : 0;
    }

    /**
     * 빈 자리 수 = 정원 - 현재 입장 인원
     * 스케줄러가 이 값을 보고 대기열에서 다음 유저를 꺼냄
     */
    public long getAvailableSlots() {
        return Math.max(0, capacity - getActiveCount());
    }

    /**
     * 설정된 정원 반환 (컨트롤러 로그용)
     */
    public long getCapacity() {
        return capacity;
    }
}