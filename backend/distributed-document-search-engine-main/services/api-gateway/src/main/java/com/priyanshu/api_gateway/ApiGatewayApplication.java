package com.priyanshu.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

	private static final Logger logger = LoggerFactory.getLogger(ApiGatewayApplication.class);

	public static void main(String[] args) {
		logger.info("Starting API Gateway Application");
		try {
			SpringApplication.run(ApiGatewayApplication.class, args);
			logger.info("API Gateway Application started successfully");
		} catch (Exception e) {
			logger.error("Failed to start API Gateway Application", e);
			throw e;
		}
	}

}
