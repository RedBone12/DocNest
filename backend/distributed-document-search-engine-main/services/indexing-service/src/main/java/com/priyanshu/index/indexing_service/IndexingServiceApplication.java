package com.priyanshu.index.indexing_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableRetry
@Slf4j
public class IndexingServiceApplication {

	public static void main(String[] args) {
		try {
			log.info("Starting IndexingServiceApplication");
			SpringApplication.run(IndexingServiceApplication.class, args);
			log.info("IndexingServiceApplication started successfully");
		} catch (Exception e) {
			log.error("Failed to start IndexingServiceApplication: {}", e.getMessage(), e);
			throw e;
		}
	}

}
