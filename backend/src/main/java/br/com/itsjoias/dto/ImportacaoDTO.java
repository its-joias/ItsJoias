package br.com.itsjoias.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Importação de maleta por PDF.
 *
 * Fluxo: LER (envia o PDF, recebe a prévia) -> VALIDAR (a cada correção, recebe a prévia conferida)
 * -> CONFIRMAR (grava tudo de uma vez). Nada é gravado antes da confirmação.
 */
public final class ImportacaoDTO {

	private ImportacaoDTO() {
	}

	public enum NivelAviso {
		/** Impede a confirmação até ser corrigido. */
		ERRO,
		/** Merece conferência, mas não impede. */
		ALERTA,
		/** Só informação. */
		INFO
	}

	public enum DecisaoPreco {
		/** Mantém o preço já cadastrado do modelo. */
		MANTER,
		/** Troca o preço do modelo pelo do documento. */
		ATUALIZAR
	}

	public record Aviso(NivelAviso nivel, String mensagem) {
	}

	/** O arquivo enviado (hash = impressão digital SHA-256: o mesmo arquivo dá sempre o mesmo valor). */
	public record ArquivoInfo(String nome, String hash, long tamanho, String metodo, int paginas) {
	}

	/** O que veio do topo/rodapé do documento (só para conferência). */
	public record DocumentoInfo(String fornecedorNome, String cnpj, String pedido, LocalDate dataCriacao,
			LocalDate dataAcerto, BigDecimal valorTotalDocumento) {
	}

	/** Dados da maleta que será criada (editáveis). Fornecedor: um existente OU um novo. */
	public record DadosMaleta(Long fornecedorId, String fornecedorNovoNome, String fornecedorCnpj, String codigo,
			String nome, LocalDate dataEntrada, LocalDate dataAcerto) {
	}

	/** Uma linha do documento, como está na pré-visualização (editável). */
	public record LinhaImportacao(
			int indice,
			boolean importar,
			String codigo,
			String descricao,
			Long categoriaId,
			Integer quantidade,
			BigDecimal valorUnitario,
			/** A pessoa conferiu no papel um código que não passa no dígito verificador. */
			boolean codigoConferido,
			DecisaoPreco decisaoPreco,
			/** Somente leitura: como a linha foi lida e o total que o documento trazia. */
			String textoOriginal,
			BigDecimal valorTotalLido,
			boolean quantidadeDeduzida) {
	}

	public record ProdutoExistente(long id, String codigo, String nome, BigDecimal precoVenda, long categoriaId,
			String categoria) {
	}

	public record LinhaConferida(LinhaImportacao linha, BigDecimal valorTotal, List<Aviso> avisos,
			ProdutoExistente produtoExistente, boolean temErro) {
	}

	public record ImportacaoAnterior(long id, OffsetDateTime data, String usuario, String maleta, int pecas) {
	}

	public record Resumo(int linhas, int aImportar, int ignoradas, int comErro, int pecas, BigDecimal valorTotal,
			int modelosNovos, int modelosExistentes) {
	}

	public record Opcao(long id, String nome) {
	}

	public record Previa(
			ArquivoInfo arquivo,
			DocumentoInfo documento,
			DadosMaleta maleta,
			List<Aviso> avisosMaleta,
			List<LinhaConferida> linhas,
			List<Aviso> avisosGerais,
			List<ImportacaoAnterior> importacoesDoMesmoArquivo,
			Resumo resumo,
			boolean podeConfirmar,
			List<Opcao> categorias,
			List<Opcao> fornecedores) {
	}

	/** O que a tela envia para validar ou confirmar. */
	public record PedidoImportacao(ArquivoInfo arquivo, DocumentoInfo documento, DadosMaleta maleta,
			List<LinhaImportacao> linhas, boolean confirmarReimportacao) {
	}

	public record EtiquetaGerada(String etiqueta, String produto, String codigoFornecedor, BigDecimal preco) {
	}

	public record Resultado(long importacaoId, long maletaId, String maletaNome, int pecas, int modelosNovos,
			int linhasIgnoradas, BigDecimal valorTotal, List<EtiquetaGerada> etiquetas) {
	}

	public record HistoricoImportacao(long id, OffsetDateTime data, String arquivo, String usuario, String fornecedor,
			String maleta, long maletaId, int pecas, int modelosNovos, int ignoradas, BigDecimal valorTotal,
			String metodo) {
	}

	/** Situação de uma leitura de PDF feita em segundo plano (PDF escaneado pode levar minutos). */
	public enum SituacaoLeitura {
		NA_FILA, LENDO, PRONTA, ERRO
	}

	/** Acompanhamento da leitura: a tela consulta até vir PRONTA (com a prévia) ou ERRO (com a mensagem). */
	public record LeituraAndamento(String id, SituacaoLeitura situacao, long segundos, Previa previa, String mensagem) {
	}

}
