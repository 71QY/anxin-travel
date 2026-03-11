package com.anxin.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import javax.annotation.PostConstruct;

@SpringBootApplication
public class TravelApplication {

    private final Environment environment;

    public TravelApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(TravelApplication.class, args);
    }

    @PostConstruct
    public void init() {
        String password = environment.getProperty("spring.datasource.password");
        System.out.println("============= 数据源密码: " + password + " =============");
    }
}