package com.quaysidepay.sepagateway;

import org.springframework.boot.SpringApplication;

public class TestSepagatewayApplication {

	public static void main(String[] args) {
		SpringApplication.from(SepagatewayApplication::main).with(PostgresTestcontainersConfig.class).run(args);
	}

}
