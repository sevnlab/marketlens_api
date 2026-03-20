package com.marketlens.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity // JPA 에서는 Entity 를 사용함으로써 JPA 엔터티로 관리해야함
@Table(name = "MEMBER")
public class Member implements Serializable {

    @Id
    @Column(name = "MEMBER_ID", nullable = false)
    // 유효성 추가할것 @validatin
//    @Pattern(regexp = "^[ㄱ-ㅎ가-힣a-z0-9-_]{2,10}$", message = "닉네임은 특수문자를 제외한 2~10자리여야 합니다.")
    private String memberId;

    @Column(name = "PASSWORD")
    private String password; // 비번은 나중에 해시키로 저장해야함

    @Column(name = "NAME", nullable = true)
    private String name;

    @Column(name = "EMAIL", nullable = false)
    private String email;

//    @Column(name = "IS_SOCIAL_LOGIN", nullable = true)
//    private boolean isSocialLogin;
}
