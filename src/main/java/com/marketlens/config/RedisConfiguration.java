package com.marketlens.config;

import com.marketlens.service.RedisSubscriberService;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 설정 클래스
 *
 * @Profile({"real", "local"}) 로 운영/로컬 환경 모두 활성화
 *
 * ─── 멀티 인스턴스 문제 배경 ───────────────────────────────────────────
 * SSE emitter는 각 서버 인스턴스의 메모리(ConcurrentHashMap)에만 존재.
 * Kafka Consumer가 A 서버에서 메시지를 수신했더라도,
 * 브라우저가 B 서버에 SSE 연결 중이면 A 서버는 emitter를 찾지 못해 SSE 전송 불가.
 *
 * 예시:
 *   브라우저 → localhost:7777 (로컬 IntelliJ) 에 SSE 연결
 *   Kafka Consumer → VM 서버(192.168.87.138)가 partition-0 담당
 *   → VM 서버에는 emitter 없음 → SSE 전송 실패 → 사용자 대기 화면에서 영원히 못 나감
 *
 * 해결책: Redis Pub/Sub
 *   Kafka Consumer → Redis "queue:admitted" 채널에 발행
 *   모든 서버 인스턴스가 해당 채널 구독
 *   → emitter를 보유한 인스턴스만 SSE 전송 (나머지는 무시)
 * ──────────────────────────────────────────────────────────────────────
 */
@Configuration
@Profile({"real", "local"})
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

    /**
     * Redis Pub/Sub 리스너 컨테이너
     *
     * 모든 서버 인스턴스가 "queue:admitted" 채널을 구독.
     * Kafka Consumer 가 해당 채널에 발행하면, 각 인스턴스의 RedisSubscriberService.onMessage() 가 호출됨.
     * → emitter 를 보유한 인스턴스만 실제로 SSE 전송
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            RedisSubscriberService subscriberService) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        // "queue:admitted" 채널 구독 등록
        container.addMessageListener(subscriberService, new ChannelTopic(RedisSubscriberService.ADMITTED_CHANNEL));

        return container;
    }
}
