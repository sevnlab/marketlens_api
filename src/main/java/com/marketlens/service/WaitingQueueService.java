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

    // Redis Key
    private static final String QUEUE_KEY = "waiting-queue";
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
     *
     * @param userId 유저 ID
     * @return 입장 토큰, 없으면 null
     */
    public String getEntryToken(String userId) {
        return stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
    }
}