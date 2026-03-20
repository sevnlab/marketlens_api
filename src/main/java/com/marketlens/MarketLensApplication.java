package com.marketlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketLensApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketLensApplication.class, args);
    }
}
