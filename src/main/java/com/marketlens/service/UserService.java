package com.marketlens.service;

import com.marketlens.dto.SignInRequest;
import com.marketlens.dto.SignInResponse;
import com.marketlens.entity.Member;
import com.marketlens.repository.SignUpRepository;
import com.marketlens.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SignUpRepository signUpRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    /**
     * 회원가입
     */
    @Transactional
    public void signUp(Member member) {
        signUpRepository.save(member);
    }

    /**
     * 로그인
     * DB에서 아이디 + 비밀번호로 조회, 없으면 예외 발생
     */
    public SignInResponse signIn(SignInRequest request) {
        Member member = signUpRepository.findByMemberIdAndPassword(request.getMemberId(), request.getPassword())
                .orElseThrow(() -> new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다."));

        SignInResponse response = new SignInResponse();
        response.setMemberId(member.getMemberId());
        response.setName(member.getName());
        response.setEmail(member.getEmail());
        return response;
    }

    /**
     * memberId로 회원 조회 (소셜 로그인 시 기존 회원 여부 확인용)
     */
    public Member findById(String memberId) {
        return userRepository.findByMemberId(memberId);
    }
}