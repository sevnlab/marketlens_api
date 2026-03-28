package com.marketlens.service;

import com.marketlens.entity.AccountInfo;
import com.marketlens.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private static final String ACCOUNT_KEY = "testAccount9999";

    @BeforeEach
    @Transactional
    void beforeEach() {
        // 테스트 데이터 초기화
        accountRepository.deleteAll();

        AccountInfo accountInfo = AccountInfo.builder()
                .accountKey(ACCOUNT_KEY)
                .totalAmt(2_000_000L)
                .build();

        accountRepository.save(accountInfo);
    }

    @Test
    @DisplayName("낙관적 락 충돌 테스트")
    void optimisticLockTest() throws InterruptedException {

        int numberOfExecute = 100;                 // 총 요청 수
        int threadCount = 10;                      // 동시 실행 스레드 수
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService service = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(numberOfExecute);

        for (int i = 0; i < numberOfExecute; i++) {
            service.execute(() -> {
                try {
                    // 매번 fresh한 Entity 로드
                    accountService.useAmountOptimisticTest(ACCOUNT_KEY, 100L);
                    successCount.incrementAndGet();
                    System.out.println(Thread.currentThread().getName() + " → 성공");
                } catch (ObjectOptimisticLockingFailureException e) {
                    System.out.println(Thread.currentThread().getName() + " → 충돌 감지");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 종료 대기
        service.shutdown();

        System.out.println("성공 횟수 = " + successCount.get());

        // 낙관적 락이 제대로 동작했다면 최소 1회만 성공하거나 일부만 성공
        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(successCount.get()).isLessThan(numberOfExecute);

        // 실제 남은 잔액 확인 (version 증가 포함)
        AccountInfo finalAccount = accountRepository.findById(ACCOUNT_KEY).orElseThrow();
        System.out.println("최종 totalAmt = " + finalAccount.getTotalAmt());
        System.out.println("최종 version = " + finalAccount.getVersion());
    }
}
