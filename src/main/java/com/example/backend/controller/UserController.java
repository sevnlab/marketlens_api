package com.example.backend.controller;

import com.example.backend.config.JwtTokenProvider;
import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.Login;
import com.example.backend.dto.SignInRequest;
import com.example.backend.dto.SignInResponse;
import com.example.backend.entity.Member;
import com.example.backend.service.LoginAttemptService;
import com.example.backend.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:3000") // 占쌔댐옙 占쏙옙占쏙옙占쏙옙占쏙옙占쏙옙占쏙옙 占쏙옙청占쏙옙 占쏙옙占?
public class UserController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // application.properties占쏙옙 占쏙옙占실듸옙 占쏙옙占쏙옙占쏙옙 占쏙옙占쌉뱄옙占쏙옙
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

    /**
     * 회원가입
     */
    @PostMapping("/signUp")
    public ResponseEntity<String> signUp(@RequestBody Member member) {
        System.out.println("user ======" + member.toString());

        userService.signUp(member);
        return ResponseEntity.ok("회占쏙옙占쏙옙占쏙옙占쏙옙 占싹뤄옙퓸占쏙옙占쏙옙求占?");
    }

    /**
     * 로그인
     */
    @PostMapping("/signIn")
    public ApiResponse<SignInResponse> signIn(@RequestBody SignInRequest request) {
        System.out.println("요청 파라미터. : " + request);

        // 계정 잠금 여부 먼저 확인 (3회 연속 실패 시 24시간 잠금)
        if (loginAttemptService.isLocked(request.getMemberId())) {
            return ApiResponse.fail("로그인 3회 연속 실패로 오늘 자정까지 로그인이 제한됩니다.");
        }

        try {
            SignInResponse signInResponse = userService.signIn(request);

            // 로그인 성공 시 실패 횟수 초기화
            loginAttemptService.loginSucceeded(request.getMemberId());

            return ApiResponse.success("정상적으로 처리되었습니다.", signInResponse);
        } catch (Exception e) {
            // 로그인 실패 시 횟수 증가
            loginAttemptService.loginFailed(request.getMemberId());

            // 실패 후 잠금 상태가 됐는지 확인
            if (loginAttemptService.isLocked(request.getMemberId())) {
                return ApiResponse.fail("로그인 3회 연속 실패로 오늘 자정까지 로그인이 제한됩니다.");
            }

            return ApiResponse.fail(e.getMessage());
        }

//        try {
//            Authentication authentication = authenticationManager.authenticate(
//                    new UsernamePasswordAuthenticationToken(request.getMemberId(), request.getPassword())
//            );
//
//            String token = jwtTokenProvider.generateToken(authentication, "regular");
//            return ResponseEntity.ok(new Login.res(token));
//        } catch (BadCredentialsException e) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패: 아이디 또는 비밀번호가 틀렸습니다.");
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
//        }
    }

    // 占쏙옙占싱뱄옙 占싸깍옙占쏙옙
    @GetMapping("/oauth/naver")
    public ResponseEntity<?> redirectNaverLogin() {
        String state = UUID.randomUUID().toString();

        String naverUrl = "https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id="
                + naverClientId + "&redirect_uri=" + URLEncoder.encode(naverRedirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;

        // properties 占쏙옙占싹울옙占쏙옙 占쏙옙占쏙옙占쏙옙 clientId占쏙옙 redirectUri占쏙옙 URL 占쏙옙占쏙옙
//        String naverUrl = "https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id="
//                + naverClientId + "&redirect_uri=" + URLEncoder.encode(naverRedirectUri, StandardCharsets.UTF_8)
//                + "&state=" + state;

        System.out.println(naverUrl);

        Map<String, String> response = new HashMap<>();
        response.put("redirectUrl", naverUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/oauth2/callback/naver")
    public ResponseEntity<?> handleNaverCallback2(@RequestParam String code, @RequestParam String state) {
        System.out.println("占식띰옙占쏙옙占?占쏙옙회 ==> " + code);
        System.out.println("占식띰옙占쏙옙占?占쏙옙회 ==> " + state);

        String tokenUrl = "https://nid.naver.com/oauth2.0/token?grant_type=authorization_code"
                + "&client_id=" + naverClientId
                + "&client_secret=" + naverClientSecret
                + "&code=" + code
                + "&state=" + state;

        // RestTemplate占쏙옙 占싱울옙占쏙옙 占쌓쇽옙占쏙옙 占쏙옙큰 占쏙옙청
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, null, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            // 占쌓쇽옙占쏙옙 占쏙옙큰 占쏙옙占쏙옙
            String responseBody = response.getBody();
            System.out.println("占쏙옙큰 占쏙옙占쏙옙: " + responseBody);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode tokenJson = objectMapper.readTree(responseBody);
                String accessToken = tokenJson.get("access_token").asText();
                System.out.println("占쌓쇽옙占쏙옙 占쏙옙큰: " + accessToken);

                // 占쌓쇽옙占쏙옙 占쏙옙큰占쏙옙 占싱울옙占쏙옙 占쏙옙占쏙옙占?占쏙옙占쏙옙 占쏙옙청
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + accessToken);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> userInfoResponse = restTemplate.exchange(
                        "https://openapi.naver.com/v1/nid/me",
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                // 占쏙옙占쏙옙占?占쏙옙占쏙옙 占쏙옙占쏙옙 처占쏙옙
                String userInfo = userInfoResponse.getBody();
                JsonNode userInfoJson = objectMapper.readTree(userInfo);

                // 占쏙옙占쏙옙占?ID 占쏙옙占쏙옙占쏙옙占쏙옙
                String id = userInfoJson.get("response").get("id").asText();

                // 占쏙옙占쏙옙占?占쏙옙占쏙옙 占쏙옙占쏙옙 확占쏙옙
                Member existingUser = userService.findById(id);
                if (existingUser == null) {
                    // 占싱곤옙占쏙옙占쌘몌옙 회占쏙옙 占쏙옙占?처占쏙옙
                    String cleanBirthday = userInfoJson.get("response").get("birthday").asText().replace("-", "");
                    String cleanMobile = userInfoJson.get("response").get("mobile").asText().replace("-", "");

                    Member newUser = new Member();
                    newUser.setMemberId(id);
                    newUser.setEmail(userInfoJson.get("response").get("email").asText());
                    newUser.setName(userInfoJson.get("response").get("name").asText());
//                    newUser.setMobile(cleanMobile);
//                    newUser.setBIRTH(userInfoJson.get("response").get("birthyear").asText() + cleanBirthday);
//                    newUser.setGENDER(userInfoJson.get("response").get("gender").asText());

                    // 占쏙옙占싱뱄옙 占싸깍옙占쏙옙占쏙옙占쏙옙 占쏙옙溝占?占쏙옙占쏙옙占쏙옙譴퓐占?占쏙옙橘占싫ｏ옙占?占쏙옙占쏙옙占쏙옙占쏙옙 占쏙옙占쏙옙
//                    newUser.setSocialLogin(true);

                    // 회占쏙옙 占쏙옙占?
                    userService.signUp(newUser);

                    // 占쏙옙占쏙옙 占쏙옙占쌉듸옙 占쏙옙占쏙옙米占?占쏙옙占쏙옙 占쏙옙占쏙옙
                    existingUser = newUser;
                }

                // JWT 占쏙옙큰 占쏙옙占쏙옙 (占싹뱄옙 占싸깍옙占싸곤옙 占쏙옙占쏙옙)
                Authentication authentication = new UsernamePasswordAuthenticationToken(existingUser, null, new ArrayList<>());
                String token = jwtTokenProvider.generateToken(authentication, "naver");

                // 占쏙옙큰占쏙옙 클占쏙옙占싱억옙트占쏙옙占쏙옙 占쏙옙占쏙옙占쏙옙占쏙옙 占쏙옙占쏙옙

                System.out.println(ResponseEntity.ok(Map.of("token", token)));
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "http://localhost:3000/oauth2/callback/naver?token=" + token)
                        .build();

//                return ResponseEntity.ok(Map.of("token", token));  // 占쌤쇽옙 JSON 占쏙옙占쏙옙

//                return ResponseEntity.status(HttpStatus.FOUND)
//                        .header(HttpHeaders.LOCATION, "http://localhost:3000/oauth2/callback/naver?token=" + token)
//                        .build();

//                return ResponseEntity.ok(new Login.res(token));

//                return ResponseEntity.ok("占쏙옙占싱뱄옙 占싸깍옙占쏙옙 占쏙옙占쏙옙")

            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("JSON 처占쏙옙 占쏙옙占쏙옙 占쌩삼옙");
            }

        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("占쏙옙占싱뱄옙 占싸깍옙占쏙옙 占쏙옙占쏙옙");
        }
    }

    // 카카占쏙옙 占싸깍옙占쏙옙
    @GetMapping("/oauth/kakao")
    public ResponseEntity<?> redirectKakaoLogin() {
        // properties 占쏙옙占싹울옙占쏙옙 占쏙옙占쏙옙占쏙옙 clientId占쏙옙 redirectUri占쏙옙 URL 占쏙옙占쏙옙
        String kakaoUrl = "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id="
                + kakaoClientId + "&redirect_uri=" + URLEncoder.encode(kakaoRedirectUri, StandardCharsets.UTF_8);

        Map<String, String> response = new HashMap<>();
        response.put("redirectUrl", kakaoUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<?> handleKakaoCallback(@RequestParam String code) {
        // 카카占쏙옙 占쏙옙큰 占쌩깍옙 占쏙옙 占쏙옙占쏙옙占?占쏙옙占쏙옙 占쏙옙청 처占쏙옙
        // 占쏙옙큰 占쌩깍옙 占쏙옙 클占쏙옙占싱억옙트占쏙옙 占십울옙占쏙옙 占쏙옙占쏙옙占쏙옙 占쏙옙占쏙옙
        return ResponseEntity.ok("카카占쏙옙 占싸깍옙占쏙옙 占쏙옙占쏙옙");
    }
}
