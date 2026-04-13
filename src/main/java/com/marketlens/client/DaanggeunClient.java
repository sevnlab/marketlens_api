package com.marketlens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketlens.dto.SearchItem;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 당근마켓 검색 클라이언트
 * Next.js SSR 페이지의 __NEXT_DATA__ 스크립트 태그에서 JSON 데이터 추출
 */
@Slf4j
@Component
public class DaanggeunClient {

    private static final ObjectMapper mapper = new ObjectMapper();

    public List<SearchItem> search(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://www.daangn.com/search/" + encoded + "?in=전국";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(6000)
                    .get();

            Element nextData = doc.getElementById("__NEXT_DATA__");
            if (nextData == null) {
                log.warn("[당근마켓] __NEXT_DATA__ 없음");
                return Collections.emptyList();
            }

            JsonNode root = mapper.readTree(nextData.html());
            JsonNode items = findItemsNode(root);
            if (items == null || !items.isArray() || items.isEmpty()) {
                log.warn("[당근마켓] items 노드 없음 또는 비어있음");
                return Collections.emptyList();
            }

            List<SearchItem> result = new ArrayList<>();
            for (JsonNode item : items) {
                String id      = text(item, "id", "articleId", "itemId");
                String title   = text(item, "name", "title", "content");
                String price   = formatPrice(text(item, "price"));
                String image   = normalizeUrl(text(item, "thumbnail", "thumbnailUrl", "image"));
                String link    = "https://www.daangn.com/articles/" + id;
                String loc     = text(item, "regionName", "location", "region");
                result.add(new SearchItem("당근마켓", title, price, image, link, loc, ""));
            }

            log.info("[당근마켓] {}건 - keyword: {}", result.size(), keyword);
            return result;

        } catch (Exception e) {
            log.warn("[당근마켓] 검색 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 당근마켓 Next.js 버전에 따라 JSON 경로가 달라질 수 있어 여러 경로 시도 */
    private JsonNode findItemsNode(JsonNode root) {
        String[] paths = {
            "/props/pageProps/searchResult/items",
            "/props/pageProps/data/articles",
            "/props/pageProps/initialData/items",
            "/props/pageProps/articles",
            "/props/pageProps/searchItems",
        };
        for (String path : paths) {
            JsonNode node = root.at(path);
            if (node != null && !node.isMissingNode() && node.isArray() && !node.isEmpty()) {
                return node;
            }
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