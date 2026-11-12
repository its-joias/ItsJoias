package br.com.itsjoias.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalendarioCobrancaTest {

	@ParameterizedTest(name = "hoje {0} -> próximo dia 5 em {1}")
	@CsvSource({
			"2026-11-01, 2026-11-05",
			"2026-11-04, 2026-11-05",
			"2026-11-05, 2026-11-05", // no próprio dia 5 ainda é o dia 5 deste mês
			"2026-11-06, 2026-12-05", // do dia 6 em diante, vira o mês
			"2026-11-30, 2026-12-05",
			"2026-12-06, 2027-01-05", // virada de ano
			"2026-01-31, 2026-02-05" })
	void proximoDia5(LocalDate hoje, LocalDate esperado) {
		assertThat(CalendarioCobranca.proximoDia5(hoje)).isEqualTo(esperado);
	}

	@Test
	void hojeEhODiaDeSaoPauloENaoODoServidorEmUtc() {
		// 06/11 02:59 em UTC = 05/11 23:59 em São Paulo (UTC-3)
		Clock antesDaMeiaNoiteEmSP = Clock.fixed(Instant.parse("2026-11-06T02:59:59Z"), ZoneOffset.UTC);
		// 06/11 03:00 em UTC = 06/11 00:00 em São Paulo
		Clock meiaNoiteEmSP = Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneOffset.UTC);

		assertThat(new CalendarioCobranca(antesDaMeiaNoiteEmSP).hoje()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(new CalendarioCobranca(meiaNoiteEmSP).hoje()).isEqualTo(LocalDate.of(2026, 11, 6));
	}

	@Test
	void hojeNaoDependeDoFusoDoRelogio() {
		Instant instante = Instant.parse("2026-11-06T02:30:00Z");
		Clock relogioEmTokyo = Clock.fixed(instante, java.time.ZoneId.of("Asia/Tokyo"));

		assertThat(new CalendarioCobranca(relogioEmTokyo).hoje()).isEqualTo(LocalDate.of(2026, 11, 5));
	}

}
