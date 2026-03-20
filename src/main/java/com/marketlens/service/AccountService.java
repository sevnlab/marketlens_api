package com.marketlens.service;

import com.marketlens.dto.UseRequest;
import com.marketlens.entity.AccountInfo;
import com.marketlens.repository.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.persistence.OptimisticLockException;

/**
 * 계좌 금액 차감 서비스
 * 낙관적 락 / 비관적 락 두 가지 방식 제공 (동시성 테스트용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /**
     * 비관적 락 (Pessimistic Lock)
     *
     * SELECT FOR UPDATE로 DB가 직접 row-level lock 수행
     * 트랜잭션 커밋 시 락 해제, 동시 접근 시 다른 세션은 대기
     * → 충돌이 많은 환경에서 유리 (충돌이 나도 재시도 없이 순서대로 처리)
     */
    @Transactional
    public void useAmountPessimistic(UseRequest request) {
        // 1. SELECT FOR UPDATE로 락 걸고 조회
        AccountInfo account = accountRepository.findByIdForUpdate(request.getAccountKey())
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        // 2. 잔액 확인
        if (account.getTotalAmt() < request.getAmount()) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        // 3. 차감 후 저장
        account.setTotalAmt(account.getTotalAmt() - request.getAmount());
        accountRepository.save(account);
    }

    /**
     * 낙관적 락 (Optimistic Lock)
     *
     * version 컬럼으로 충돌 감지: UPDATE 시 WHERE version=? 조건 추가
     * 충돌 발생 시 OptimisticLockException 발생 → 호출부에서 재시도 처리
     * → 충돌이 드문 환경에서 유리 (락 없이 낙관적으로 저장 시도)
     */
    @Transactional
    public void useAmountOptimistic(UseRequest request) {
        // 1. 계좌 조회
        AccountInfo account = accountRepository.findById(request.getAccountKey())
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        log.info("{} - 조회: version={}, totalAmt={}",
                Thread.currentThread().getName(), account.getVersion(), account.getTotalAmt());

        // 2. 잔액 확인
        if (account.getTotalAmt() < request.getAmount()) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        // 3. 차감
        account.setTotalAmt(account.getTotalAmt() - request.getAmount());

        // 4. 저장 (version 불일치 시 OptimisticLockException 발생)
        try {
            accountRepository.save(account);
            log.info("{} - UPDATE 완료 후 version={}", Thread.currentThread().getName(), account.getVersion());
        } catch (OptimisticLockException e) {
            log.warn("{} - 낙관적 락 충돌 발생: {}", Thread.currentThread().getName(), e.getMessage());
            throw e;
        }
    }

    /**
     * 낙관적 락 동시성 테스트용 (AccountServiceTest에서 사용)
     * useAmountOptimistic과 동일하나 남은 잔액을 int로 반환
     */
    @Transactional
    public int useAmountOptimisticTest(String accountKey, long useAmt) {
        AccountInfo account = accountRepository.findById(accountKey)
                .orElseThrow(() -> new IllegalArgumentException("계좌를 찾을 수 없습니다."));

        log.info("{} - 조회: version={}, totalAmt={}",
                Thread.currentThread().getName(), account.getVersion(), account.getTotalAmt());

        if (account.getTotalAmt() < useAmt) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        account.setTotalAmt(account.getTotalAmt() - useAmt);

        try {
            accountRepository.save(account);
            log.info("{} - UPDATE 완료 후 version={}", Thread.currentThread().getName(), account.getVersion());
        } catch (OptimisticLockException e) {
            log.warn("{} - 낙관적 락 충돌 발생: {}", Thread.currentThread().getName(), e.getMessage());
            throw e;
        }

        return (int) account.getTotalAmt().longValue();
    }
}