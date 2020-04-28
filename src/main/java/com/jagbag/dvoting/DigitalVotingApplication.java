package com.jagbag.dvoting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Home of the main method. Launches the Spring framework.
 */
@SpringBootApplication
public class DigitalVotingApplication {
	public static void main(String[] args) {
		SpringApplication.run(DigitalVotingApplication.class, args);
	}

}
