package com.marketlens.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


/**
 * ? 포인트 사용 이력 테이블
 *  - 오라클 시퀀스 SEVEN.SEQ_POINT_HIST 를 이용하여 자동 증가
 */
@Entity
@Table(name = "TB_POINT_HIST")
@Getter
@Setter
public class PointHist {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_POINT_HIST") // PK를 어떻게 생성할지 지정 (strategy, generator)
    @SequenceGenerator(
            name = "SEQ_POINT_HIST", // 실제 DB 시퀀스명 연결
            sequenceName = "SEVEN.SEQ_POINT_HIST", // 오라클 실제 시퀀스 이름
            allocationSize = 1 // 하나씩 증가
    )

    @Column(name = "SEQ_NO")
    private Long seqNo;

    @Column(name = "ACCOUNT_KEY")
    private String accountKey;

    @Column(name = "USE_AMT")
    private Long useAmt;

    @Column(name = "REM_AMT")
    private Long remAmt;

    @Column(name = "REG_DT")
    private String regDt;
}
