package com.marketlens.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 로컬 개발 환경용 로그인 시도 관리 (아무 동작 안 함)
 *
 * Redis가 없는 로컬 환경에서는 계정 잠금 기능을 사용하지 않음
 * local 프로파일에서만 활성화되며, real 프로파일에서는 RedisLoginAttemptService가 사용됨
 */
@Service
@Profile("local")
public class NoOpLoginAttemptService implements LoginAttemptService {

    @Override
    public void loginFailed(String memberId) {
        // 로컬 환경에서는 실패 횟수를 기록하지 않음
    }

    @Override
    public void loginSucceeded(String memberId) {
        // 로컬 환경에서는 아무 처리 안 함
    }

    @Override
    public boolean isLocked(String memberId) {
        // 로컬 환경에서는 항상 잠금 해제 상태
        return false;
    }
}