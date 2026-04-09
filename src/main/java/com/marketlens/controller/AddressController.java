package com.marketlens.controller;

import com.marketlens.client.AddressClient;
import com.marketlens.dto.AddressRequest;
import com.marketlens.dto.AddressResponse;
import com.marketlens.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressClient addressClient;

    @PostMapping("/search")
    public ApiResponse<List<AddressResponse>> search(@RequestBody AddressRequest request) {
        return ApiResponse.success("주소 검색 성공", addressClient.search(request));
    }
}
