package com.marketlens.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddressResponse {
    private String zipCode;       // 우편번호
    private String roadAddress;   // 도로명주소 (시도 시군구 도로명 건물번호)
    private String jibunAddress;  // 지번주소
}