package com.marketlens.dto;

import lombok.Getter;

@Getter
public class AddressRequest {
    private String currentPage;
    private String countPerPage;
    private String keyword;
}
