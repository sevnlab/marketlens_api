package com.marketlens.dto;

import lombok.Data;

@Data
public class SignInRequest {

    /**
     * 회원아이디
     */
    private String memberId;

    /**
     * 비밀번호
     */
    private String password;

}
