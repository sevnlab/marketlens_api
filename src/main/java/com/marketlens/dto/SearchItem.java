package com.marketlens.dto;

/**
 * 검색 결과 공통 DTO
 * 번개장터 / 당근마켓 / 중고나라 모두 이 형태로 통일
 */
public record SearchItem(
        String platform,   // 번개장터 | 당근마켓 | 중고나라
        String title,
        String price,
        String imageUrl,
        String link,
        String location,
        String time
) {}