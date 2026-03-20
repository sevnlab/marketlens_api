package com.marketlens.repository;

import com.marketlens.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Member, Long> {
    Member findByMemberId(String memberId);  // userId로 사용자 조회
}
