package com.marketlens.dto;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.io.Serializable;

@Getter
@Setter
@ToString
@Entity
@IdClass(UserPrimaryKey.class) // 복합 기본 키 클래스를 지정합니다.
public class User implements Serializable {
    @Id
    @Column(name = "email")
    private String email = "";

    @Id
    @Column(name = "email")
    private String password = "";

    @Id
    @Column(name = "email")
    private String name = "";

    public User() {
        // 기본 생성자 추가
    }

}
