package com.ecoterminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcoTerminalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcoTerminalApplication.class, args);
    }
}
