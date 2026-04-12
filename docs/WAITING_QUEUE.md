# 대기열 시스템 (Waiting Queue System)

## 개요

트래픽이 몰리는 상황에서 DB 부하를 방지하기 위해 설계된 **입장 제한 대기열 시스템**입니다.  
Redis Sorted Set으로 순서를 관리하고, Kafka로 DB 처리 속도를 제어하며, SSE로 실시간 순번을 클라이언트에 전달합니다.

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| 대기열 관리 | Redis Sorted Set |
| 입장 처리 메시지 브로커 | Apache Kafka 3.x (spring-kafka) |
| 실시간 순번 알림 | SSE (Server-Sent Events) |
| 다중 서버 SSE 브로드캐스트 | Redis Pub/Sub |

---

## 전체 흐름

```
브라우저
  │
  ├─ POST /api/queue/enter     → Redis 대기열 진입 (sessionId 발급)
  ├─ GET  /api/queue/stream    → SSE 구독 (실시간 순번 수신)
  │
  └─ [스케줄러 3초마다]
       ├─ TTL 만료 유저 강제 퇴장 (evict expired)
       ├─ 빈 자리만큼 Redis 대기열 → Kafka publish
       └─ 남은 대기자 순번 SSE push

[Kafka Consumer]
  └─ 메시지 수신 → 입장 토큰 발급 → Redis active-users 등록
                 → Redis Pub/Sub "queue:admitted" 발행
                 → (모든 서버) RedisSubscriberService → SSE 전송
```

---

## Redis 데이터 구조

| Key | 타입 | member | score | 용도 |
|-----|------|--------|-------|------|
| `waiting-queue` | Sorted Set | sessionId | 진입 시각 (epoch ms) | 대기 순서 관리 |
| `active-users` | Sorted Set | entryToken | 만료 시각 (epoch ms) | 입장 정원 관리 |

**식별자 분리 이유:**
- `sessionId`: JS 메모리에만 존재 → 새로고침 시 소멸 = 순번 초기화 (티켓팅과 동일)
- `entryToken`: localStorage 저장 → Secret 페이지에서 자리 반납 식별자로 사용

---

## Kafka 구성

| 항목 | 값 | 설명 |
|------|----|------|
| 토픽 | `kafka-waiting-queue` | 입장 허용 유저 전달 토픽 |
| 파티션 | 3 | Consumer 스레드 수와 동일하게 설정 |
| replica | 1 | 단일 브로커 환경 (클러스터 시 2~3 권장) |
| acks | `all` | ISR 전체 복제 확인 후 ack (데이터 유실 방지) |
| auto-offset-reset | `earliest` | 새 컨슈머 그룹은 가장 오래된 메시지부터 |
| enable-auto-commit | `false` | 처리 완료 후 수동 commit (`acknowledge()`) |
| consumer group | `waiting-queue-group` | 파티션 분담 처리 |
| concurrency | 3 | 파티션 수와 동일 (1 파티션 = 1 스레드) |

---

## 다중 서버 환경에서의 SSE 문제 해결

**문제:**  
Kafka Consumer는 파티션을 서버 인스턴스에 분배합니다.  
브라우저가 연결된 서버와 Kafka Consumer가 실행 중인 서버가 다를 경우, Consumer 서버에 해당 sessionId의 emitter가 없어 SSE 전송이 불가능합니다.

```
로컬(localhost:7777): 브라우저 SSE 연결 → emitter 존재
VM(192.168.87.138):  partition-0 담당 → 메시지 수신 → emitter 없음 → SSE 전송 실패
```

**해결:**  
Kafka Consumer는 입장 처리(토큰 발급/정원 등록)만 수행하고,  
SSE 알림은 **Redis Pub/Sub**(`queue:admitted`)을 통해 전체 인스턴스에 브로드캐스트합니다.  
emitter를 보유한 인스턴스만 실제 SSE를 전송합니다.

```
Kafka Consumer (어떤 서버든)
  └─ 입장 처리 완료
       └─ Redis Pub/Sub publish ("queue:admitted", "sessionId:entryToken")
            └─ [모든 서버] RedisSubscriberService.onMessage()
                 └─ emitter 보유 서버만 SSE 전송
```

---

## API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/queue/enter` | 대기열 진입 (sessionId 발급) |
| GET | `/api/queue/status?sessionId=` | 내 순번 조회 |
| GET | `/api/queue/stream?sessionId=` | SSE 구독 (실시간 순번/입장 알림) |
| DELETE | `/api/queue/cancel?sessionId=` | 대기열 취소 |
| POST | `/api/queue/leave?entryToken=` | 퇴장 (자리 반납) |
| POST | `/api/queue/exit` | 브라우저 종료 시 정리 (sendBeacon용) |

모든 엔드포인트는 **JWT 인증 필요** (로그인 사용자만 접근 가능)

---

## 설정값 (application.yml)

```yaml
queue:
  capacity: 2              # 동시 입장 허용 최대 인원
  batch-size: 10           # 스케줄러 1회 최대 처리 인원
  active-ttl-minutes: 10   # 입장 후 최대 체류 시간 (분) — 초과 시 강제 퇴장

kafka:
  topic:
    waiting-queue: kafka-waiting-queue
```

---

## 관련 클래스

| 클래스 | 역할 |
|--------|------|
| `WaitingQueueService` | Redis 대기열/정원 관리 |
| `WaitingQueueScheduler` | 3초 주기 스케줄러 (TTL 정리 → Kafka publish → SSE push) |
| `KafkaProducerService` | Kafka 메시지 발행 |
| `KafkaConsumerService` | Kafka 메시지 수신 → 입장 처리 → Redis Pub/Sub 발행 |
| `SseEmitterService` | SSE 연결 관리 및 이벤트 전송 |
| `WaitingQueueController` | REST API 엔드포인트 |
| `KafkaConfig` | Kafka 토픽/컨슈머 팩토리 설정 |