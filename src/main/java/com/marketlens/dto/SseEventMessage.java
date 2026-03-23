package com.marketlens.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Redis Pub/Sub 을 통해 서버 간 전달되는 SSE 이벤트 메시지 DTO
 *
 * 멀티 서버 환경에서 Kafka Consumer 가 실행되는 서버와
 * 사용자의 SSE 연결이 맺어진 서버가 다를 수 있음.
 * → Kafka Consumer / Scheduler 가 직접 SseEmitter 를 호출하는 대신
 *   Redis Pub/Sub 채널에 발행 → 모든 서버가 수신 → 자기 서버에 emitter 가 있는 서버만 전송
 *
 * type 값:
 *   - "rank"     : 대기 순번 업데이트 (data: {rank, total})
 *   - "admitted" : 입장 허용 (data: {entryToken, message})
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseEventMessage {

    /** 이벤트 종류: "rank" | "admitted" */
    private String type;

    /** 대상 유저 ID */
    private String userId;

    /** 이벤트 데이터 (rank/total 또는 entryToken 등) */
    private Map<String, Object> data;
}