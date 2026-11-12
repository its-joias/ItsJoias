package br.com.itsjoias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.itsjoias.dto.VendaDTO.FormaVenda;
import br.com.itsjoias.dto.VendaDTO.ParcelaPrevia;
import br.com.itsjoias.exception.RegraNegocioException;

class CalculadoraVendaTest {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

	private static BigDecimal r(String v) {
		return new BigDecimal(v);
	}

	@Test
	void centavosQueSobramVaoNaUltimaParcela() {
		assertThat(CalculadoraVenda.dividir(r("100.00"), 3)).containsExactly(r("33.33"), r("33.33"), r("33.34"));
		assertThat(CalculadoraVenda.dividir(r("144.89"), 3)).containsExactly(r("48.29"), r("48.29"), r("48.31"));
		assertThat(CalculadoraVenda.dividir(r("90.00"), 3)).containsExactly(r("30.00"), r("30.00"), r("30.00"));
	}

	@ParameterizedTest(name = "venda em {0}, {1} mês(es) depois -> {2}")
	@CsvSource({
			"2026-11-11, 1, 2026-12-05",
			"2026-11-03, 1, 2026-12-05", // antes do dia 5 também vai para o mês seguinte
			"2026-11-05, 1, 2026-12-05",
			"2026-12-20, 1, 2027-01-05", // virada de ano
			"2026-01-31, 1, 2026-02-05", // mês curto
			"2026-11-11, 3, 2027-02-05" })
	void parcelasVencemNoDia5APartirDoMesSeguinte(LocalDate venda, int meses, LocalDate esperado) {
		assertThat(CalculadoraVenda.vencimento(venda, meses)).isEqualTo(esperado);
	}

	@Test
	void acrescimoDe10PorCentoArredondadoNoCentavo() {
		assertThat(CalculadoraVenda.acrescimo(r("149.90"), true)).isEqualTo(r("14.99"));
		assertThat(CalculadoraVenda.acrescimo(r("0.05"), true)).isEqualTo(r("0.01"));
		assertThat(CalculadoraVenda.acrescimo(r("149.90"), false)).isEqualTo(r("0.00"));
	}

	@Test
	void aVistaEhUmaParcelaPagaNoDia() {
		assertThat(CalculadoraVenda.parcelas(HOJE, r("164.89"), FormaVenda.A_VISTA, 5, null))
				.containsExactly(new ParcelaPrevia(1, r("164.89"), HOJE, true));
	}

	@Test
	void parceladoSemEntrada() {
		List<ParcelaPrevia> p = CalculadoraVenda.parcelas(HOJE, r("100.00"), FormaVenda.PARCELADO, 3, null);
		assertThat(p).extracting(ParcelaPrevia::numero, ParcelaPrevia::valor, ParcelaPrevia::vencimento,
				ParcelaPrevia::pagaNaVenda).containsExactly(
						tuple(1, r("33.33"), LocalDate.of(2026, 12, 5), false),
						tuple(2, r("33.33"), LocalDate.of(2027, 1, 5), false),
						tuple(3, r("33.34"), LocalDate.of(2027, 2, 5), false));
	}

	@Test
	void parceladoComEntradaPagaNaHora() {
		List<ParcelaPrevia> p = CalculadoraVenda.parcelas(HOJE, r("164.89"), FormaVenda.PARCELADO, 3, r("20.00"));
		assertThat(p).extracting(ParcelaPrevia::numero, ParcelaPrevia::valor, ParcelaPrevia::vencimento,
				ParcelaPrevia::pagaNaVenda).containsExactly(
						tuple(1, r("20.00"), HOJE, true),
						tuple(2, r("48.29"), LocalDate.of(2026, 12, 5), false),
						tuple(3, r("48.29"), LocalDate.of(2027, 1, 5), false),
						tuple(4, r("48.31"), LocalDate.of(2027, 2, 5), false));
		BigDecimal soma = p.stream().map(ParcelaPrevia::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(soma).isEqualTo(r("164.89"));
	}

	@Test
	void entradaZeroEhComoNaoTerEntrada() {
		assertThat(CalculadoraVenda.parcelas(HOJE, r("100.00"), FormaVenda.PARCELADO, 2, r("0.00"))).hasSize(2);
	}

	@Test
	void entradaIgualOuMaiorQueOTotalEhRecusada() {
		assertThatThrownBy(() -> CalculadoraVenda.parcelas(HOJE, r("100.00"), FormaVenda.PARCELADO, 2, r("100.00")))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("menor que o total");
	}

	@Test
	void valorPequenoDemaisParaAsParcelas() {
		assertThatThrownBy(() -> CalculadoraVenda.parcelas(HOJE, r("0.05"), FormaVenda.PARCELADO, 10, null))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("pequeno demais");
	}

}
