package com.enterprise.flashsale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlashsaleApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlashsaleApplication.class, args);
	}

}
