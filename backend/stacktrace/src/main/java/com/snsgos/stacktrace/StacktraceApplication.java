package com.snsgos.stacktrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StacktraceApplication {

	public static void main(String[] args) {
		SpringApplication.run(StacktraceApplication.class, args);
	}

}
