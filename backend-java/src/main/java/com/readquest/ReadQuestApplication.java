package com.readquest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@MapperScan("com.readquest.mapper")
public class ReadQuestApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReadQuestApplication.class, args);
  }
}
