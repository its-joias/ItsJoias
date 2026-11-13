package br.com.itsjoias.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Formatos da venda: o pedido que vem do front, a prévia e a venda gravada. */
public final class VendaDTO {

	private VendaDTO() {
	}

	/** Maior número de parcelas aceito (limite técnico; não é regra comercial combinada). */
	public static final int MAX_PARCELAS = 24;

	public enum FormaVenda {
		/** 1 parcela, vence no dia da venda e é paga na hora. */
		A_VISTA,
		/** N parcelas nos dias 5 a partir do mês seguinte; entrada opcional paga na hora. */
		PARCELADO
	}

	/** Mesmos valores aceitos pela coluna pagamentos.forma_pagamento. */
	public enum FormaPagamento {
		PIX, DINHEIRO, CARTAO, TRANSFERENCIA, OUTRO
	}

	/** Pedido de venda (usado tanto na prévia quanto na confirmação). */
	public record VendaForm(
			@NotNull(message = "Escolha a cliente.")
			Long clienteId,

			@NotEmpty(message = "Inclua pelo menos uma peça.")
			@Size(max = 50, message = "No máximo 50 peças por venda.")
			List<String> codigos,

			/** true = soma 10% sobre o valor das peças. */
			boolean acrescimo10,

			@NotNull(message = "Escolha à vista ou parcelado.")
			FormaVenda forma,

			@Min(value = 1, message = "No mínimo 1 parcela.")
			@Max(value = MAX_PARCELAS, message = "No máximo " + MAX_PARCELAS + " parcelas.")
			Integer numeroParcelas,

			@DecimalMin(value = "0.00", message = "A entrada não pode ser negativa.")
			@Digits(integer = 10, fraction = 2, message = "Use no máximo 2 casas decimais.")
			BigDecimal entrada,

			/** Obrigatória quando algo é pago na hora (à vista ou entrada). */
			FormaPagamento formaPagamento) {
	}

	public record ItemPrevia(
			long pecaId,
			String etiqueta,
			String produto,
			String categoria,
			String maleta,
			BigDecimal preco) {
	}

	public record ParcelaPrevia(
			int numero,
			BigDecimal valor,
			LocalDate vencimento,
			/** true = registrada como paga na hora da venda (à vista ou entrada). */
			boolean pagaNaVenda) {
	}

	public record Previa(
			long clienteId,
			String clienteNome,
			LocalDate dataVenda,
			List<ItemPrevia> itens,
			BigDecimal valorProdutos,
			BigDecimal acrescimo,
			BigDecimal valorTotal,
			List<ParcelaPrevia> parcelas,
			BigDecimal pagoNaVenda,
			BigDecimal saldoAPagar) {
	}

	public record VendaCriada(long vendaId, Previa venda) {
	}

	/** Linha da lista de vendas recentes. */
	public record VendaResumo(
			long vendaId,
			LocalDate dataVenda,
			long clienteId,
			String clienteNome,
			int quantidadePecas,
			BigDecimal valorTotal,
			BigDecimal pago,
			BigDecimal saldo,
			int parcelas,
			String status) {
	}

	/** Peça para a busca da venda e para a tela de produtos. */
	public record PecaResumo(
			long pecaId,
			String etiqueta,
			String produtoCodigo,
			String produto,
			String categoria,
			String maleta,
			BigDecimal preco,
			String situacao) {
	}

	/** Peça de uma venda já registrada (preço congelado no dia da venda). */
	public record ItemVendido(String etiqueta, String produto, String maleta, BigDecimal preco) {
	}

	public record ParcelaDetalhe(int numero, LocalDate vencimento, BigDecimal valor, BigDecimal pago, String status) {
	}

	public record PagamentoDetalhe(LocalDate data, BigDecimal valor, String forma, boolean estornado, String motivoEstorno) {
	}

	/** Venda completa, para o histórico da cliente. */
	public record VendaDetalhe(
			long vendaId,
			LocalDate dataVenda,
			long clienteId,
			String clienteNome,
			String status,
			String vendidaPor,
			BigDecimal valorProdutos,
			BigDecimal acrescimo,
			BigDecimal valorTotal,
			OffsetDateTime canceladaEm,
			String motivoCancelamento,
			List<ItemVendido> itens,
			List<ParcelaDetalhe> parcelas,
			List<PagamentoDetalhe> pagamentos) {
	}

	public record CancelamentoForm(String motivo) {
	}

}
