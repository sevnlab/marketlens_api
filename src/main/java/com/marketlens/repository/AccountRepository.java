package com.marketlens.repository;

import com.marketlens.entity.AccountInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountInfo, String> {



    /**
     * ? 비관적 락 (SELECT FOR UPDATE)
     *  - 해당 row를 트랜잭션 종료까지 락으로 묶음
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM AccountInfo a WHERE a.accountKey = :accountKey")
    Optional<AccountInfo> findByIdForUpdate(@Param("accountKey") String accountKey);
}
