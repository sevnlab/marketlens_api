package com.marketlens.dto;

public record AddressResponse(
        String zipCode,       // 우편번호
        String roadAddress,   // 도로명주소
        String jibunAddress   // 지번주소
) {}