package com.marketlens.client;

import com.marketlens.dto.AddressRequest;
import com.marketlens.dto.AddressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddressClient {

    private final RestTemplate restTemplate;

    @Value("${address.service.url}")
    private String addressServiceUrl;

    @Value("${address.service.confirm-key}")
    private String confirmKey;

    @Value("${address.fallback.url}")
    private String addressFallbackUrl;

    public List<AddressResponse> search(AddressRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // RestTemplate 이 APPLICATION_FORM_URLENCODED 타입으로 Form 전송할 때, 내부적으로 MultiValueMap만 Form 인코딩으로 변환함
        // Map<String, String>을 넣으면 Form이 아니라 그냥 문자열로 직렬화돼버립니다. 값이 하나여도 Form 전송을 하려면 MultiValueMap을 써야 합니다. Spring의 제약
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("keyword", request.getKeyword());
        params.add("currentPage", request.getCurrentPage());
        params.add("countPerPage", request.getCountPerPage());
        params.add("confmKey", confirmKey);
        params.add("resultType", "json");

        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers); // headers + body를 하나로 묶어서 RestTemplate 에 넘기는 래퍼 객체

        JusoApiResponse response;
        try {
            response = restTemplate.postForObject(addressServiceUrl, httpEntity, JusoApiResponse.class);
        } catch (RestClientException e) {
            // 연결 실패, 타임아웃, 4xx/5xx 등 네트워크/HTTP 오류 → SQLite 로컬 앱으로 fallback
            log.warn("[주소검색] Juso API 통신 오류 - fallback 전환. keyword: {}, error: {}", request.getKeyword(), e.getMessage());
            return searchFallback(request);
        }

        if (response == null || response.results() == null) {
            return Collections.emptyList();
        }

        // Juso API 비즈니스 오류 - HTTP 200이지만 errorCode != "0"
        String errorCode = response.results().common().errorCode();
        if (!"0".equals(errorCode)) {
            log.error("[주소검색] Juso API 오류 응답 - errorCode: {}, errorMessage: {}",
                    errorCode, response.results().common().errorMessage());
            throw new RuntimeException("주소 검색 API 오류: " + response.results().common().errorMessage());
        }

        if (response.results().juso() == null) {
            return Collections.emptyList();
        }

        // 내 API 스펙에 맞춰 리턴
        return response.results().juso().stream()
                .map(juso -> new AddressResponse(juso.zipNo(), juso.roadAddr(), juso.jibunAddr()))
                .collect(Collectors.toList());
    }

    private List<AddressResponse> searchFallback(AddressRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddressRequest> httpEntity = new HttpEntity<>(request, headers);

        try {
            FallbackApiResponse response = restTemplate.postForObject(addressFallbackUrl, httpEntity, FallbackApiResponse.class);

            if (response == null || response.data() == null || response.data().results() == null) {
                return Collections.emptyList();
            }

            return response.data().results();

        } catch (RestClientException e) {
            log.error("[주소검색] Fallback 통신 오류 - keyword: {}, error: {}", request.getKeyword(), e.getMessage());
            throw new RuntimeException("주소 검색 서비스를 일시적으로 사용할 수 없습니다.", e);
        }
    }

    record JusoApiResponse(Results results) {
        record Results(Common common, List<Juso> juso) {}
        record Common(String errorCode, String errorMessage) {}
        record Juso(String roadAddr, String jibunAddr, String zipNo) {}
    }

    record FallbackApiResponse(String message, FallbackSearchResult data) {
        record FallbackSearchResult(int totalCount, int currentPage, int countPerPage, List<AddressResponse> results) {}
    }
}