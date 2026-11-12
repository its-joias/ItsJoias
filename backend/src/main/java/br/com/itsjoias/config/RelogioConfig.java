package br.com.itsjoias.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação. Fica num bean para os testes poderem "parar o tempo"
 * (Clock.fixed) e conferir viradas de dia, de mês e de fuso.
 */
@Configuration
public class RelogioConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
