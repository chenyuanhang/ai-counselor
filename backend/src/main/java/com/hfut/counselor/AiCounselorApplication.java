package com.hfut.counselor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hfut.counselor.mapper")
public class AiCounselorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCounselorApplication.class, args);
    }
}
