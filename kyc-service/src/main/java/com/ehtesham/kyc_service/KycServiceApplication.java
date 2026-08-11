package com.ehtesham.kyc_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableKafka
@EnableAsync
// Bug fix: required for OutboxPublisher's @Scheduled job to actually
// run — without this, the outbox events written by KycEventPublisher
// would accumulate and never be delivered.
@EnableScheduling
public class KycServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KycServiceApplication.class, args);
	}

}
