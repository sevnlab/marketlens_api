-- 로컬 개발용 초기 데이터 (local 프로파일 전용)
-- H2 인메모리 DB에 앱 시작 시 자동으로 삽입됨
-- Oracle(dev/test/real)에서는 이 파일이 실행되지 않음 (ddl-auto: create-drop 이 local에서만 동작)
-- @Entity 달린 자바 파일이 없으면 테이블을 자동으로 못만듬

INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, EMAIL)
VALUES ('admin', '1234', '관리자', 'admin@marketlens.com');

INSERT INTO MEMBER (MEMBER_ID, PASSWORD, NAME, EMAIL)
VALUES ('test', '1234', '테스트유저', 'test@marketlens.com');
