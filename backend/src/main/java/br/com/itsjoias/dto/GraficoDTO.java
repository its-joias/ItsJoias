package br.com.itsjoias.dto;

import java.math.BigDecimal;

/** Dados prontos para os gráficos (as contas são feitas no banco; a tela só desenha). */
public final class GraficoDTO {

	private GraficoDTO() {
	}

	/** Mês no formato "2026-11". */
	public record VendasDoMes(String mes, BigDecimal vendido, int vendas) {
	}

	/** Parcelas que vencem no mês: quanto já entrou e quanto falta (atrasado é parte do que falta). */
	public record RecebimentosDoMes(String mes, BigDecimal recebido, BigDecimal aReceber, BigDecimal atrasado) {
	}

	/** Peças das maletas ativas, por categoria. */
	public record PecasDaCategoria(String categoria, int disponiveis, int vendidas, int perdidasOuDevolvidas) {
	}

}
