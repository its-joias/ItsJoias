package br.com.itsjoias.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** O que foi entendido de um documento de entrega de maleta (antes de qualquer conferência). */
public record DocumentoLido(Cabecalho cabecalho, List<Linha> linhas) {

	/** Dados do topo e do rodapé do documento. Qualquer um pode vir vazio (null). */
	public record Cabecalho(
			String fornecedorNome,
			String cnpj,
			String pedido,
			LocalDate dataCriacao,
			LocalDate dataAcerto,
			BigDecimal valorTotalItens) {
	}

	/**
	 * Uma linha de peça. Os campos podem vir vazios quando o documento (ou o OCR) não permitiu ler.
	 * quantidadeDeduzida = true quando a quantidade não foi lida e veio da conta total ÷ valor unitário.
	 */
	public record Linha(
			String codigo,
			String descricao,
			Integer quantidade,
			BigDecimal valorUnitario,
			BigDecimal valorTotal,
			boolean quantidadeDeduzida,
			String textoOriginal) {

		public Linha comCodigo(String novo) {
			return new Linha(novo, descricao, quantidade, valorUnitario, valorTotal, quantidadeDeduzida, textoOriginal);
		}

		public Linha comDescricao(String nova) {
			return new Linha(codigo, nova, quantidade, valorUnitario, valorTotal, quantidadeDeduzida, textoOriginal);
		}

		public Linha comValores(Integer qtd, BigDecimal unitario, BigDecimal total, boolean deduzida) {
			return new Linha(codigo, descricao, qtd, unitario, total, deduzida, textoOriginal);
		}

		/** Quantidade × valor unitário confere com o valor total lido? (null = não dá para saber) */
		public Boolean valoresBatem() {
			if (quantidade == null || valorUnitario == null || valorTotal == null) {
				return null;
			}
			return valorUnitario.multiply(BigDecimal.valueOf(quantidade)).compareTo(valorTotal) == 0;
		}
	}

}
