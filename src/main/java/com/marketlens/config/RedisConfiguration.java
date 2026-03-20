package com.marketlens.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 설정 클래스 (운영 환경 전용)
 *
 * @Profile("real") 로 인해 real 프로파일에서만 로드됨
 * local 프로파일에서는 이 클래스가 로드되지 않아 Redis 연결을 시도하지 않음
 *
 * 실행 환경별 동작:
 *   local → Redis 비활성화 (application-local.yml 참고)
 *   real  → Redis Cluster 연결 (application-real.yml 참고)
 */
@Configuration
@Profile("real")
@EnableCaching
public class RedisConfiguration {

    /**
     * 단순 문자열 저장/조회용 RedisTemplate
     * opsForValue().set("key", "value") 형태로 사용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * JSON 직렬화 기반 RedisTemplate
     * Key: String, Value: JSON (Object 저장 가능)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key, HashKey 는 String 으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value, HashValue 는 JSON 으로 직렬화 (어떤 객체든 저장 가능)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    /**
     * Spring Cache (@Cacheable, @CacheEvict 등) 를 Redis 로 처리하는 설정
     * 기본 TTL 5분, null 값은 캐싱하지 않음
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))   // 캐시 기본 만료 시간: 5분
                .disableCachingNullValues()          // null 은 캐시에 저장하지 않음
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
