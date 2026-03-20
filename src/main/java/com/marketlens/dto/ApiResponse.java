package com.marketlens.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class ApiResponse<T> {

    private String resCd;
    private String resMsg;

    @JsonInclude(JsonInclude.Include.NON_NULL) // null 이면 JSON 응답에서 data 필드 자체를 제거
    private T data;

    public static <T> ApiResponse<T> success(String resMsg, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setResCd("S000");
        response.setResMsg(resMsg);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> fail(String resMsg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setResCd("S001");
        response.setResMsg(resMsg);
        return response;
    }
}
