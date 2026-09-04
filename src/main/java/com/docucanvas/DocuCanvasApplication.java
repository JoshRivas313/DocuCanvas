package com.docucanvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableCaching
@ConfigurationPropertiesScan
@SpringBootApplication
public class DocuCanvasApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocuCanvasApplication.class, args);
	}

}
