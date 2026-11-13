package br.com.itsjoias.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de GET /api/painel. Todos os números já vêm calculados: o front só exibe.
 * Dinheiro é sempre BigDecimal com 2 casas.
 */
public record PainelDTO(
		LocalDate hoje,
		Totais totais,
		List<ParcelaAtrasada> parcelasAtrasadas,
		List<ParcelaAVencer> vencemNoProximoDia5,
		List<MaletaAtiva> maletasAtivas) {

	public record Totais(
			TotalVendido totalVendido,
			Recebido recebido,
			AReceber aReceber,
			Atrasado atrasado,
			ClientesDevendo clientesDevendo,
			ProximoDia5 proximoDia5) {
	}

	/** Soma de valor_total das vendas FINALIZADAS. */
	public record TotalVendido(BigDecimal valor, long quantidadeVendas) {
	}

	/** Pagamentos não estornados. percentualDoVendido tem 1 casa e é null quando nada foi vendido. */
	public record Recebido(BigDecimal valor, BigDecimal percentualDoVendido) {
	}

	/** totalVendido − recebido; quantidadeParcelas = parcelas com saldo > 0. */
	public record AReceber(BigDecimal valor, long quantidadeParcelas) {
	}

	/** Saldo das parcelas vencidas (vencimento < hoje) e ainda não quitadas. */
	public record Atrasado(BigDecimal valor, long quantidadeParcelas) {
	}

	public record ClientesDevendo(long quantidade, long comParcelaAtrasada) {
	}

	/** Saldo em aberto das parcelas que vencem na data do próximo dia 5. */
	public record ProximoDia5(LocalDate data, BigDecimal valor, long quantidadeParcelas) {
	}

	public record ParcelaAtrasada(
			long parcelaId,
			long vendaId,
			long clienteId,
			String clienteNome,
			int numeroParcela,
			int totalParcelas,
			LocalDate dataVencimento,
			BigDecimal valorParcela,
			BigDecimal valorPago,
			BigDecimal saldo,
			SituacaoParcela situacao,
			int diasAtraso) {
	}

	public record ParcelaAVencer(
			long parcelaId,
			long vendaId,
			long clienteId,
			String clienteNome,
			int numeroParcela,
			int totalParcelas,
			LocalDate dataVencimento,
			BigDecimal valorParcela,
			BigDecimal saldo,
			SituacaoParcela situacao) {
	}

	public record MaletaAtiva(
			long maletaId,
			String codigo,
			String nome,
			String fornecedor,
			LocalDate dataEntrada,
			int pecasRecebidas,
			int pecasVendidas,
			int pecasDisponiveis,
			int pecasPerdidas,
			int pecasDevolvidas,
			int pecasPerdidasOuDevolvidas,
			BigDecimal custoTotal,
			BigDecimal totalVendido) {
	}

	/** Mesmos nomes de vw_saldo_parcelas.situacao. */
	public enum SituacaoParcela {
		PENDENTE, PARCIAL, PAGA
	}

}
