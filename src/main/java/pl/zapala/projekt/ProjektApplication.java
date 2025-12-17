package pl.zapala.projekt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjektApplication {
	public static void main(String[] args) {
		SpringApplication.run(ProjektApplication.class, args);
	}
}