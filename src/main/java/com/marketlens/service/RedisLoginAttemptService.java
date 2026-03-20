package com.marketlens.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 로그인 시도 횟수 관리 (운영 환경 전용)
 *
 * Redis Key 구조:
 *   login:fail:{memberId} → 실패 횟수 (1~3), TTL 당일 자정까지
 *
 * 동작 방식:
 *   - 실패 시: 횟수 +1, 첫 실패 시 당일 자정까지 TTL 설정
 *   - 성공 시: 키 삭제 (횟수 초기화)
 *   - 3회 실패 시: 당일 자정까지 로그인 불가 (다음날 00:00 자동 해제)
 *
 * 2대 이상 서버에서도 Redis를 공유하므로 서버 간 횟수가 동기화됨
 */
@Service
@Profile("real")
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_FAIL_COUNT = 3;

    private final StringRedisTemplate redisTemplate;

    public RedisLoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 현재 시각부터 오늘 자정(00:00:00)까지 남은 초를 계산
     * 예: 23:30 에 실패하면 30분(1800초) 후 자동 해제
     */
    private long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @Override
    public void loginFailed(String memberId) {
        String key = FAIL_KEY_PREFIX + memberId;

        // setIfAbsent: 키가 없을 때만 "0" 으로 초기화하면서 자정까지 TTL 동시 설정
        // expire() 를 별도 호출하면 Redisson pExpire 버그로 StackOverflowError 발생하므로
        // TTL 은 최초 키 생성 시점에 SET key 0 EX {seconds} NX 명령으로 한 번에 처리
        redisTemplate.opsForValue().setIfAbsent(key, "0", secondsUntilMidnight(), TimeUnit.SECONDS);

        // 이후 INCR 로 횟수 증가 (TTL 은 유지됨)
        redisTemplate.opsForValue().increment(key);
    }

    @Override
    public void loginSucceeded(String memberId) {
        // 로그인 성공 시 실패 횟수 초기화
        redisTemplate.delete(FAIL_KEY_PREFIX + memberId);
    }

    @Override
    public boolean isLocked(String memberId) {
        String value = redisTemplate.opsForValue().get(FAIL_KEY_PREFIX + memberId);

        if (value == null) return false;

        return Integer.parseInt(value) >= MAX_FAIL_COUNT;
    }
}