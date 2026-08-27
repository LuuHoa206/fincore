package com.luuhoa.fincore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinCoreBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinCoreBackendApplication.class, args);
	}

}
