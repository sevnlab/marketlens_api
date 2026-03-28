package com.marketlens;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

// 실제 Redis 서버를 사용하는 테스트 지원 클래스
@SpringBootTest
public abstract class RedisTestContainerSupport {

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanData() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }
}
