package com.marketlens.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_ACCOUNT_INFO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfo {

    @Id
    @Column(name = "ACCOUNT_KEY")
    private String accountKey;

    @Column(name = "TOTAL_AMT")
    private Long totalAmt;

    /**
     * ? 낙관적 락(Optimistic Lock)을 위한 버전 컬럼
     *  - JPA가 UPDATE 시 version 조건을 자동으로 WHERE 절에 추가함
     *  - ex) UPDATE ... WHERE ACCOUNT_KEY=? AND VERSION=?
     *  - 충돌 발생 시 OptimisticLockException 발생
     */
    @Version
    private Long version; // 낙관적 락용 버전 컬럼
}
