package br.com.poc.starter.srv.hex;

import br.com.poc.enge.logcloud.spring.EnableLogCloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@SpringBootApplication
@EnableLogCloud
@EnableKafka
public class Application implements CommandLineRunner {

	private static final Logger LOGGER_TECNICO = LoggerFactory.getLogger(Application.class);

	private final Environment environment;

	public Application(Environment environment) { this.environment = environment; }

	public static void main(String[] args) { SpringApplication.run(Application.class, args); }

	@Override
	public void run(String... args) throws Exception {
		String activeProfile = Arrays.toString(this.environment.getActiveProfiles());
		String profiles = defaultIfBlank(activeProfile.replace("[]", ""), "[DEFAULT]");
		LOGGER_TECNICO.info("ACTIVE PROFILES: {}", profiles);
	}
}
