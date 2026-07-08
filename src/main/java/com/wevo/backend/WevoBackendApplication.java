package com.wevo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WevoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(WevoBackendApplication.class, args);
	}

}
