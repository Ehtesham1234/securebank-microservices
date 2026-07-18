package com.ehtesham.securebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient

public class SecureBankApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecureBankApplication.class, args);
	}

}
