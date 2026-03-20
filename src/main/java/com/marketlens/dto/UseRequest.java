package com.marketlens.dto;


import lombok.Data;

/**
 * 클라이언트에서 보내는 요청 데이터
 */
@Data
public class UseRequest {
    private String accountKey; // 계좌키
    private Long amount; // 사용된 금액
}
