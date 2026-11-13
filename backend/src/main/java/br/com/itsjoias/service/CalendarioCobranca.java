package br.com.itsjoias.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * Datas de cobrança.
 *
 * "Hoje" é sempre o dia em São Paulo, não o do servidor (que costuma estar em UTC:
 * das 21h à meia-noite o UTC já está no dia seguinte e o atraso sairia errado).
 */
@Component
public class CalendarioCobranca {

	public static final ZoneId FUSO_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	/** As parcelas vencem sempre neste dia do mês. */
	public static final int DIA_VENCIMENTO = 5;

	private final Clock clock;

	public CalendarioCobranca(Clock clock) {
		this.clock = clock;
	}

	/** A data de hoje em São Paulo, qualquer que seja o fuso do servidor. */
	public LocalDate hoje() {
		return clock.instant().atZone(FUSO_SAO_PAULO).toLocalDate();
	}

	/**
	 * Próximo dia 5 a partir de {@code hoje}: o dia 5 deste mês se ainda não passou
	 * (inclusive no próprio dia 5, para a vendedora ver quem paga hoje);
	 * do dia 6 em diante, o dia 5 do mês seguinte.
	 */
	public static LocalDate proximoDia5(LocalDate hoje) {
		LocalDate dia5DesteMes = hoje.withDayOfMonth(DIA_VENCIMENTO);
		return hoje.isAfter(dia5DesteMes) ? dia5DesteMes.plusMonths(1) : dia5DesteMes;
	}

}
