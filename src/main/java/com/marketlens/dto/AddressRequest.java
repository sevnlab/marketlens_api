package com.marketlens.dto;

import lombok.Getter;

@Getter
public class AddressRequest {
    private int currentPage;
    private int countPerPage;
    private String keyword;
}
