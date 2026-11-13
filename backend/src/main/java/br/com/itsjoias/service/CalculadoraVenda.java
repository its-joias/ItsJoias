package br.com.itsjoias.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.itsjoias.dto.VendaDTO.FormaVenda;
import br.com.itsjoias.dto.VendaDTO.ParcelaPrevia;
import br.com.itsjoias.exception.RegraNegocioException;

/**
 * Contas da venda, sem banco (função pura: mesma entrada, mesma saída).
 *
 * Regras combinadas para o ItsJoias:
 * - Acréscimo opcional de 10% sobre o valor das peças (arredondado ao centavo).
 * - À vista: 1 parcela, vence no dia da venda e é paga na hora.
 * - Parcelado: N parcelas iguais; os centavos que sobram vão na ÚLTIMA.
 *   A 1ª vence no dia 5 do mês seguinte à venda; as demais, nos dias 5 dos meses seguintes.
 * - Entrada (opcional, só no parcelado): vira a parcela 1, vence no dia da venda e é paga na hora;
 *   o restante é dividido nas parcelas dos dias 5.
 */
public final class CalculadoraVenda {

	private static final BigDecimal DEZ_POR_CENTO = new BigDecimal("0.10");

	private CalculadoraVenda() {
	}

	public static BigDecimal acrescimo(BigDecimal valorProdutos, boolean acrescimo10) {
		return acrescimo10 ? valorProdutos.multiply(DEZ_POR_CENTO).setScale(2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO.setScale(2);
	}

	public static List<ParcelaPrevia> parcelas(LocalDate dataVenda, BigDecimal total, FormaVenda forma,
			Integer numeroParcelas, BigDecimal entrada) {
		total = total.setScale(2, RoundingMode.UNNECESSARY);
		if (total.signum() <= 0) {
			throw new RegraNegocioException("O total da venda precisa ser maior que zero.");
		}
		if (forma == FormaVenda.A_VISTA) {
			return List.of(new ParcelaPrevia(1, total, dataVenda, true));
		}
		if (numeroParcelas == null) {
			throw new RegraNegocioException("Informe em quantas parcelas.");
		}
		List<ParcelaPrevia> parcelas = new ArrayList<>();
		BigDecimal restante = total;
		if (entrada != null && entrada.signum() > 0) {
			entrada = entrada.setScale(2, RoundingMode.UNNECESSARY);
			if (entrada.compareTo(total) >= 0) {
				throw new RegraNegocioException("A entrada precisa ser menor que o total da venda. "
						+ "Para pagar tudo agora, escolha 'À vista'.");
			}
			parcelas.add(new ParcelaPrevia(1, entrada, dataVenda, true));
			restante = total.subtract(entrada);
		}
		List<BigDecimal> valores = dividir(restante, numeroParcelas);
		for (int i = 0; i < valores.size(); i++) {
			parcelas.add(new ParcelaPrevia(parcelas.size() + 1, valores.get(i), vencimento(dataVenda, i + 1), false));
		}
		return parcelas;
	}

	/** Dia 5 do mês "mesesDepois" meses depois da venda (1 = mês seguinte). */
	public static LocalDate vencimento(LocalDate dataVenda, int mesesDepois) {
		return dataVenda.withDayOfMonth(1).plusMonths(mesesDepois).withDayOfMonth(CalendarioCobranca.DIA_VENCIMENTO);
	}

	/** Divide em n partes iguais (truncando no centavo); o que sobra vai na última. */
	static List<BigDecimal> dividir(BigDecimal valor, int n) {
		BigDecimal base = valor.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
		if (base.signum() <= 0) {
			throw new RegraNegocioException("O valor é pequeno demais para " + n + " parcelas.");
		}
		List<BigDecimal> partes = new ArrayList<>();
		for (int i = 0; i < n - 1; i++) {
			partes.add(base);
		}
		partes.add(valor.subtract(base.multiply(BigDecimal.valueOf(n - 1L))));
		return partes;
	}

}
