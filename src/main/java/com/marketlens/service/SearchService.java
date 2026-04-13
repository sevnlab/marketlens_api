package com.marketlens.service;

import com.marketlens.client.BunjangClient;
import com.marketlens.client.DaanggeunClient;
import com.marketlens.client.JoognnaraClient;
import com.marketlens.dto.SearchItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 3개 중고 마켓 병렬 검색
 * 각 플랫폼은 독립적으로 실행되며, 하나가 실패해도 나머지 결과는 정상 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BunjangClient bunjangClient;
    private final DaanggeunClient daanggeunClient;
    private final JoognnaraClient joognnaraClient;

    public List<SearchItem> search(String keyword) {
        // 3개 플랫폼 동시 요청
        CompletableFuture<List<SearchItem>> bunjang =
                CompletableFuture.supplyAsync(() -> bunjangClient.search(keyword));
        CompletableFuture<List<SearchItem>> daanggeun =
                CompletableFuture.supplyAsync(() -> daanggeunClient.search(keyword));
        CompletableFuture<List<SearchItem>> joognnara =
                CompletableFuture.supplyAsync(() -> joognnaraClient.search(keyword));

        List<SearchItem> results = new ArrayList<>();
        results.addAll(await(bunjang, "번개장터"));
        results.addAll(await(daanggeun, "당근마켓"));
        results.addAll(await(joognnara, "중고나라"));

        log.info("[검색] 총 {}건 반환 - keyword: {}", results.size(), keyword);
        return results;
    }

    private List<SearchItem> await(CompletableFuture<List<SearchItem>> future, String platform) {
        try {
            return future.get(7, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[{}] 결과 수집 실패: {}", platform, e.getMessage());
            return Collections.emptyList();
        }
    }
}