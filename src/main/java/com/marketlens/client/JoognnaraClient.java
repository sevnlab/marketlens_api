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
 * 중고나라 검색 클라이언트
 * REST API 사용
 */
@Slf4j
@Component
public class JoognnaraClient {

    private static final String API_URL = "https://api.joongna.com/v4/search/items";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public JoognnaraClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<SearchItem> search(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = API_URL + "?keyword=" + encoded + "&page=0&limit=20&sortType=RECENTLY";
            log.info("[중고나라] 요청 URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://web.joongna.com/");
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Origin", "https://web.joongna.com");

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode list = findListNode(root);
            if (list == null || !list.isArray()) return Collections.emptyList();

            List<SearchItem> items = new ArrayList<>();
            for (JsonNode item : list) {
                String seq   = text(item, "seq", "id", "productSeq");
                String title = text(item, "title", "subject", "name");
                String price = formatPrice(text(item, "price"));
                String image = normalizeUrl(text(item, "imageUrl", "imageUri", "thumbnailUrl", "image"));
                String link  = "https://web.joongna.com/product/" + seq;
                String loc   = text(item, "regionName", "location", "address", "region");
                String time  = text(item, "displayTime", "regDate", "date");
                items.add(new SearchItem("중고나라", title, price, image, link, loc, time));
            }

            log.info("[중고나라] {}건 - keyword: {}", items.size(), keyword);
            return items;

        } catch (Exception e) {
            log.warn("[중고나라] 검색 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 중고나라 API 응답 구조: { data: { list: [...] } } 또는 { list: [...] } */
    private JsonNode findListNode(JsonNode root) {
        String[] paths = { "/data/list", "/list", "/data/items", "/items", "/result/list" };
        for (String path : paths) {
            JsonNode node = root.at(path);
            if (node != null && !node.isMissingNode() && node.isArray()) return node;
        }
        return null;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull() && !v.asText("").isBlank()) return v.asText();
        }
        return "";
    }

    private String formatPrice(String raw) {
        if (raw.isBlank()) return "가격 미정";
        try {
            return String.format("%,d원", Long.parseLong(raw.replaceAll("[^0-9]", "")));
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String normalizeUrl(String url) {
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }
}