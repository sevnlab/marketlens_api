package com.marketlens.service;

/**
 * 로그인 시도 횟수 관리 인터페이스
 *
 * 프로파일에 따라 구현체가 달라짐:
 *   real  → RedisLoginAttemptService  (Redis 기반, 서버 간 공유)
 *   local → NoOpLoginAttemptService   (아무 동작 안 함, 로컬 테스트용)
 */
public interface LoginAttemptService {

    /** 로그인 실패 처리 - 실패 횟수 증가 */
    void loginFailed(String memberId);

    /** 로그인 성공 처리 - 실패 횟수 초기화 */
    void loginSucceeded(String memberId);

    /** 계정 잠김 여부 확인 (3회 연속 실패 시 자정까지 잠금) */
    boolean isLocked(String memberId);
}
