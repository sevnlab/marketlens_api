package com.marketlens.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka 설정 클래스 (운영/테스트 환경 전용)
 *
 * @Profile({"real", "test"}) 로 인해 local 프로파일에서는 로드되지 않음
 *
 * 담당 역할:
 *   - 대기열 토픽(waiting-queue) 자동 생성
 *   - Consumer 수동 commit 모드 설정 (enable-auto-commit: false 와 연동)
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.waiting-queue}")
    private String waitingQueueTopic;

    /**
     * 대기열 토픽 생성
     *
     * partitions: 동시 처리 스레드 수에 맞춰 설정 (Consumer 수와 맞춤)
     * replicas:   브로커 1대 환경이면 1, 클러스터면 2~3 권장
     */
    @Bean
    public NewTopic waitingQueueTopic() {
        return TopicBuilder.name(waitingQueueTopic)
                .partitions(3)
                .replicas(1) // 브로커 수를 초과해서 생성 불가, 1이면 복제없음., 복제를 만들면 브로커 죽었을때 복제된것이 승격
                .build();
    }

    /**
     * Kafka Listener 컨테이너 팩토리
     *
     * enable-auto-commit: false 설정에 맞게 수동 commit(MANUAL_IMMEDIATE) 모드 활성화
     * → Consumer 에서 처리 완료 후 Acknowledgment.acknowledge() 를 직접 호출해야 함
     * → 처리 실패 시 commit 을 건너뛰어 메시지 재처리 보장
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3); // 파티션 수와 동일하게 설정 (1 파티션 = 1 스레드)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
