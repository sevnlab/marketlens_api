package com.marketlens.controller;

import com.marketlens.dto.ApiResponse;
import com.marketlens.dto.SearchItem;
import com.marketlens.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://marketlens.co.kr"})
public class SearchController {

    private final SearchService searchService;

    /**
     * 중고 마켓 통합 검색
     * 번개장터 / 당근마켓 / 중고나라를 병렬로 검색해 결과를 합쳐 반환
     */
    @GetMapping("/api/search")
    public ApiResponse<List<SearchItem>> search(@RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return ApiResponse.success("검색어를 입력하세요.", Collections.emptyList());
        }
        log.info("[검색 요청] keyword: {}", q.trim());
        List<SearchItem> items = searchService.search(q.trim());
        return ApiResponse.success("검색 완료 - 총 " + items.size() + "건", items);
    }
}