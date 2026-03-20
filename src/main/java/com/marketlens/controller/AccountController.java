package com.marketlens.controller;

import com.marketlens.dto.UseRequest;
import com.marketlens.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lock 테스트용 Api 엔드포인트
 * - 동일한 key로 여러 요청이 동시에 들어올 때 동시성을 제어하는지 확인
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;

    // 낙관적 락 버전
    @PostMapping("/use/optimistic")
    public String useOptimistic(@RequestBody UseRequest request) {
        try {
            accountService.useAmountOptimistic(request);
            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "FAIL: " + e.getMessage();
        }
    }

    // 비관적 락 버전
    @PostMapping("/use/pessimistic")
    public String usePessimistic(@RequestBody UseRequest request) {
        try {
            accountService.useAmountPessimistic(request);
            return "SUCCESS";
        } catch (Exception e) {
            e.printStackTrace();
            return "FAIL: " + e.getMessage();
        }
    }
}
