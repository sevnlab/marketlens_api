package com.marketlens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketlens.dto.SearchItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 번개장터 검색 클라이언트
 * 공개 JSON API 사용 (인증 불필요)
 */
@Slf4j
@Component
public class BunjangClient {

    private static final String API_URL = "https://api.bunjang.co.kr/api/1/find_v2.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public BunjangClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<SearchItem> search(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = API_URL + "?q=" + encoded + "&page=0&n=20&order=score";

            log.info("[번개장터] 요청 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://m.bunjang.co.kr/");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            log.info("[번개장터] 응답 status: {}, body 앞 200자: {}",
                    response.getStatusCode(),
                    response.getBody() != null ? response.getBody().substring(0, Math.min(200, response.getBody().length())) : "null");

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode list = root.get("list");
            if (list == null || !list.isArray()) {
                log.warn("[번개장터] list 노드 없음. 최상위 키: {}", root.fieldNames());
                return Collections.emptyList();
            }

            List<SearchItem> items = new ArrayList<>();
            for (JsonNode item : list) {
                String pid   = text(item, "pid");
                String name  = text(item, "name");
                String price = formatPrice(text(item, "price"));
                String image = normalizeUrl(text(item, "product_image"));
                String link  = "https://m.bunjang.co.kr/products/" + pid;
                String loc   = text(item, "location");
                String time  = formatTime(text(item, "update_time"));
                items.add(new SearchItem("번개장터", name, price, image, link, loc, time));
            }

            log.info("[번개장터] {}건 - keyword: {}", items.size(), keyword);
            return items;

        } catch (Exception e) {
            log.warn("[번개장터] 검색 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText("") : "";
    }

    private String formatPrice(String raw) {
        if (raw.isBlank()) return "가격 미정";
        try {
            return String.format("%,d원", Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return raw + "원";
        }
    }

    /** // 로 시작하는 프로토콜 상대 URL → https: 보완 */
    private String normalizeUrl(String url) {
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    /** update_time은 Unix timestamp(초) */
    private String formatTime(String raw) {
        if (raw.isBlank()) return "";
        try {
            long diffSec = System.currentTimeMillis() / 1000 - Long.parseLong(raw);
            if (diffSec < 60)    return "방금 전";
            if (diffSec < 3600)  return (diffSec / 60) + "분 전";
            if (diffSec < 86400) return (diffSec / 3600) + "시간 전";
            return (diffSec / 86400) + "일 전";
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}