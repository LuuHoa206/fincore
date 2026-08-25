package com.luuhoa.fincore;

import org.springframework.boot.SpringApplication;

public class TestFinCoreBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinCoreBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
