package com.marketlens.study.serde;

import com.marketlens.study.redis.serde.DataSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class DataSerializerTest {
    @Test
    void serde() {

        MyData myData = new MyData("id", "data");

        // 문자열로 직렬화
        String serialized = DataSerializer.serializeOrException(myData);

        // 역질렬화하여 원본객체 구현
        MyData deserialized = DataSerializer.deserializeOrNull(serialized, MyData.class);

        // 원본데이터와 일치하는지 검증
        assertThat(deserialized).isEqualTo(myData);
    }

    // 불변클래스를 레코드 라는 네이밍으로 편하게 쓰기위함
    record MyData(String id, String data) {
    }


}
