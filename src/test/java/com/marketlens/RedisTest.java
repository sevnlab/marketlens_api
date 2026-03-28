package com.marketlens;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisTest extends RedisTestContainerSupport {

    @Test
    void test1() {
        // 연결 정보 출력
        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        System.out.println("Redis Connection: " + factory);


        redisTemplate.opsForValue().set("mykey", "myvalue");
        String result = redisTemplate.opsForValue().get("mykey");
        System.out.println("result = " + result);
    }

    @Test
    void test2() {
        String result = redisTemplate.opsForValue().get("mykey");
        System.out.println("result = " + result);
    }

    @Test
    void test3() {
        String result = redisTemplate.opsForValue().get("mykey");
        System.out.println("result = " + result);
    }

}
