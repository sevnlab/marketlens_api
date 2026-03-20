package com.marketlens.controller;

import com.marketlens.config.JwtTokenProvider;
import com.marketlens.dto.ApiResponse;
import com.marketlens.dto.SignInRequest;
import com.marketlens.dto.SignInResponse;
import com.marketlens.entity.Member;
import com.marketlens.service.LoginAttemptService;
import com.marketlens.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final JwtTokenProvider jwtTokenProvider;

    // ObjectMapper는 thread-safe → static으로 공유 (매 요청마다 생성 낭비 방지)
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // RestTemplate도 재사용 (매 요청마다 new RestTemplate() 생성 금지)
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String naverClientId;

    @Value("${spring.security.oauth2.client.registration.naver.client-secret}")
    private String naverClientSecret;

    @Value("${spring.security.oauth2.client.registration.naver.redirect-uri}")
    private String naverRedirectUri;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    /**
     * 회원가입
     */
    @PostMapping("/signUp")
    public ResponseEntity<String> signUp(@RequestBody Member member) {
        log.info("회원가입 요청: {}", member);
        userService.signUp(member);
        return ResponseEntity.ok("가입완료");
    }

    /**
     * 로그인
     * 3회 연속 실패 시 당일 자정까지 계정 잠금
     */
    @PostMapping("/signIn")
    public ApiResponse<SignInResponse> signIn(@RequestBody SignInRequest request,
                                              HttpServletResponse response) {
        log.info("로그인 요청 memberId: {}", request.getMemberId());

        if (loginAttemptService.isLocked(request.getMemberId())) {
            return ApiResponse.fail("로그인 3회 연속 실패로 오늘 자정까지 로그인이 제한됩니다.");
        }

        try {
            SignInResponse signInResponse = userService.signIn(request);
            loginAttemptService.loginSucceeded(request.getMemberId());

            // JWT 발급 → httpOnly Cookie로 전송
            // httpOnly: JS에서 접근 불가 (XSS 방어)
            // sameSite=Lax: CSRF 방어 (외부 사이트에서 쿠키 전송 차단)
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    request.getMemberId(), null, new ArrayList<>());
            String token = jwtTokenProvider.generateToken(authentication, "general");

            ResponseCookie cookie = ResponseCookie.from("jwt", token)
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofMillis(jwtExpirationMs))
                    .sameSite("Lax")
                    // .secure(true) // HTTPS 환경에서 활성화
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            log.info("로그인 성공 - memberId={}", request.getMemberId());

            return ApiResponse.success("정상적으로 처리되었습니다.", signInResponse);

        } catch (Exception e) {
            loginAttemptService.loginFailed(request.getMemberId());

            if (loginAttemptService.isLocked(request.getMemberId())) {
                return ApiResponse.fail("로그인 3회 연속 실패로 오늘 자정까지 로그인이 제한됩니다.");
            }
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 로그아웃
     * JWT 쿠키를 maxAge=0 으로 덮어써서 즉시 만료시킴
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("로그아웃");

        return ResponseEntity.ok(ApiResponse.success("로그아웃 되었습니다.", null));
    }

    /**
     * 네이버 로그인 URL 반환
     * 프론트엔드가 이 URL로 사용자를 리다이렉트하면 네이버 로그인 화면으로 이동
     */
    @GetMapping("/oauth/naver")
    public ResponseEntity<?> redirectNaverLogin() {
        String state = UUID.randomUUID().toString();
        String naverUrl = "https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id="
                + naverClientId
                + "&redirect_uri=" + URLEncoder.encode(naverRedirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;

        log.info("네이버 로그인 URL 생성");
        return ResponseEntity.ok(Map.of("redirectUrl", naverUrl));
    }

    /**
     * 네이버 로그인 콜백
     * 네이버 인증 완료 후 네이버가 code + state를 붙여 이 URL로 리다이렉트
     *
     * 처리 흐름:
     *   1. 인증 코드(code)로 네이버에서 액세스 토큰 발급
     *   2. 액세스 토큰으로 네이버 사용자 정보 조회
     *   3. 기존 회원이면 바로 로그인, 신규면 자동 회원가입 후 로그인
     *   4. JWT 발급 → 프론트엔드로 리다이렉트
     */
    @GetMapping("/oauth2/callback/naver")
    public ResponseEntity<?> handleNaverCallback(@RequestParam String code, @RequestParam String state) {
        log.debug("네이버 콜백 수신 - state: {}", state);

        // 1. 인증 코드로 액세스 토큰 요청
        String tokenUrl = "https://nid.naver.com/oauth2.0/token?grant_type=authorization_code"
                + "&client_id=" + naverClientId
                + "&client_secret=" + naverClientSecret
                + "&code=" + code
                + "&state=" + state;

        ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, null, String.class);
        if (tokenResponse.getStatusCode() != HttpStatus.OK) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("네이버 로그인 실패");
        }

        try {
            // 2. 액세스 토큰 파싱
            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String accessToken = tokenJson.get("access_token").asText();
            log.debug("네이버 액세스 토큰 수신 완료");

            // 3. 액세스 토큰으로 사용자 정보 요청
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            ResponseEntity<String> userInfoResponse = restTemplate.exchange(
                    "https://openapi.naver.com/v1/nid/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            // 4. 사용자 정보 파싱 (네이버는 "response" 키 안에 실제 데이터가 있음)
            JsonNode userInfo = objectMapper.readTree(userInfoResponse.getBody()).get("response");
            String naverId = userInfo.get("id").asText();

            // 5. 기존 회원 확인, 없으면 자동 회원가입
            Member member = userService.findById(naverId);
            if (member == null) {
                member = new Member();
                member.setMemberId(naverId);
                member.setEmail(userInfo.get("email").asText());
                member.setName(userInfo.get("name").asText());
                userService.signUp(member);
                log.info("네이버 신규 회원 자동 가입 - naverId={}", naverId);
            }

            // 6. JWT 발급 → 프론트엔드로 리다이렉트
            Authentication authentication = new UsernamePasswordAuthenticationToken(member, null, new ArrayList<>());
            String token = jwtTokenProvider.generateToken(authentication, "naver");

            log.info("네이버 로그인 성공 - naverId={}", naverId);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "http://localhost:3000/oauth2/callback/naver?token=" + token)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("네이버 콜백 JSON 처리 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("JSON 처리 오류");
        }
    }

    /**
     * 카카오 로그인 URL 반환
     */
    @GetMapping("/oauth/kakao")
    public ResponseEntity<?> redirectKakaoLogin() {
        String kakaoUrl = "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id="
                + kakaoClientId
                + "&redirect_uri=" + URLEncoder.encode(kakaoRedirectUri, StandardCharsets.UTF_8);

        return ResponseEntity.ok(Map.of("redirectUrl", kakaoUrl));
    }

    /**
     * 카카오 로그인 콜백 (미구현)
     * TODO: 카카오 사용자 정보 조회 및 JWT 발급 구현 예정
     */
    @GetMapping("/kakao/callback")
    public ResponseEntity<?> handleKakaoCallback(@RequestParam String code) {
        return ResponseEntity.ok("카카오 로그인 완료");
    }
}