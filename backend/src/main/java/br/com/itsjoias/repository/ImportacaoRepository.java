package br.com.itsjoias.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.itsjoias.dto.ImportacaoDTO.EtiquetaGerada;
import br.com.itsjoias.dto.ImportacaoDTO.HistoricoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.ImportacaoAnterior;
import br.com.itsjoias.dto.ImportacaoDTO.Opcao;
import br.com.itsjoias.dto.ImportacaoDTO.ProdutoExistente;

/** Banco da importação de maletas (SQL direto, como em vendas: várias tabelas numa transação). */
@Repository
public class ImportacaoRepository {

	/** Palavra-chave de categoria (categoria_palavras_chave). */
	public record PalavraChave(String termo, long categoriaId, int prioridade) {
	}

	public record Fornecedor(long id, String nome, String cnpj, boolean ativo) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	public ImportacaoRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ---- consultas para a prévia ---------------------------------------------------------

	public List<PalavraChave> palavrasChave() {
		return jdbc.query("""
				SELECT k.termo, k.categoria_id, k.prioridade
				FROM categoria_palavras_chave k JOIN categorias c ON c.id = k.categoria_id
				WHERE c.ativo
				""", (rs, i) -> new PalavraChave(rs.getString("termo"), rs.getLong("categoria_id"), rs.getInt("prioridade")));
	}

	public List<Opcao> categorias() {
		return jdbc.query("SELECT id, nome || ' (' || prefixo || ')' AS nome FROM categorias WHERE ativo ORDER BY nome",
				(rs, i) -> new Opcao(rs.getLong("id"), rs.getString("nome")));
	}

	public boolean categoriaExiste(long id) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM categorias WHERE id = :id AND ativo)",
				Map.of("id", id), Boolean.class));
	}

	public List<Fornecedor> fornecedores() {
		return jdbc.query("SELECT id, nome, cnpj, ativo FROM fornecedores ORDER BY lower(nome)",
				(rs, i) -> new Fornecedor(rs.getLong("id"), rs.getString("nome"), rs.getString("cnpj"), rs.getBoolean("ativo")));
	}

	public boolean maletaExiste(String codigo) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM maletas WHERE lower(codigo) = lower(:c))", Map.of("c", codigo), Boolean.class));
	}

	/** Modelos já cadastrados para estes códigos deste fornecedor (código -> modelo). */
	public Map<String, ProdutoExistente> produtosPorCodigoFornecedor(long fornecedorId, Collection<String> codigos) {
		Map<String, ProdutoExistente> mapa = new HashMap<>();
		if (codigos.isEmpty()) {
			return mapa;
		}
		jdbc.query("""
				SELECT pcf.codigo_fornecedor, p.id, p.codigo, p.nome, p.preco_venda, p.categoria_id, c.nome AS categoria
				FROM produto_codigos_fornecedor pcf
				JOIN produtos   p ON p.id = pcf.produto_id
				JOIN categorias c ON c.id = p.categoria_id
				WHERE pcf.fornecedor_id = :f AND pcf.codigo_fornecedor IN (:codigos)
				""", new MapSqlParameterSource("f", fornecedorId).addValue("codigos", codigos), rs -> {
			mapa.put(rs.getString("codigo_fornecedor"), new ProdutoExistente(rs.getLong("id"), rs.getString("codigo"),
					rs.getString("nome"), rs.getBigDecimal("preco_venda").setScale(2, RoundingMode.HALF_UP),
					rs.getLong("categoria_id"), rs.getString("categoria")));
		});
		return mapa;
	}

	public List<ImportacaoAnterior> importacoesDoArquivo(String hash) {
		return jdbc.query("""
				SELECT i.id, i.criado_em, u.nome AS usuario, m.nome AS maleta, i.pecas_importadas
				FROM importacoes_maleta i
				JOIN usuarios u ON u.id = i.usuario_id
				JOIN maletas  m ON m.id = i.maleta_id
				WHERE i.arquivo_hash = :h
				  AND COALESCE(m.observacao, '') NOT LIKE '[Descartada]%' -- maleta descartada não conta como já importada
				ORDER BY i.id
				""", Map.of("h", hash), (rs, i) -> new ImportacaoAnterior(rs.getLong("id"),
				rs.getObject("criado_em", OffsetDateTime.class), rs.getString("usuario"), rs.getString("maleta"),
				rs.getInt("pecas_importadas")));
	}

	public List<HistoricoImportacao> historico(int limite) {
		return jdbc.query("""
				SELECT i.id, i.criado_em, i.arquivo_nome, u.nome AS usuario, f.nome AS fornecedor, m.nome AS maleta,
				       i.maleta_id, i.pecas_importadas, i.modelos_novos, i.linhas_ignoradas, i.valor_total, i.metodo_leitura
				FROM importacoes_maleta i
				JOIN usuarios     u ON u.id = i.usuario_id
				JOIN fornecedores f ON f.id = i.fornecedor_id
				JOIN maletas      m ON m.id = i.maleta_id
				ORDER BY i.id DESC LIMIT :limite
				""", Map.of("limite", limite), (rs, i) -> new HistoricoImportacao(rs.getLong("id"),
				rs.getObject("criado_em", OffsetDateTime.class), rs.getString("arquivo_nome"), rs.getString("usuario"),
				rs.getString("fornecedor"), rs.getString("maleta"), rs.getLong("maleta_id"), rs.getInt("pecas_importadas"),
				rs.getInt("modelos_novos"), rs.getInt("linhas_ignoradas"), rs.getBigDecimal("valor_total"),
				rs.getString("metodo_leitura")));
	}

	// ---- gravação -------------------------------------------------------------------------

	public long inserirFornecedor(String nome, String cnpj) {
		return jdbc.queryForObject("INSERT INTO fornecedores (nome, cnpj) VALUES (:nome, :cnpj) RETURNING id",
				new MapSqlParameterSource("nome", nome).addValue("cnpj", cnpj), Long.class);
	}

	public void completarCnpj(long fornecedorId, String cnpj) {
		jdbc.update("UPDATE fornecedores SET cnpj = :cnpj WHERE id = :id AND cnpj IS NULL",
				new MapSqlParameterSource("id", fornecedorId).addValue("cnpj", cnpj));
	}

	public long inserirMaleta(String codigo, String nome, long fornecedorId, LocalDate entrada, LocalDate acerto) {
		return jdbc.queryForObject("""
				INSERT INTO maletas (codigo, nome, fornecedor_id, tipo, data_entrada, data_acerto, status)
				VALUES (:codigo, :nome, :fornecedor, 'NOVA', :entrada, :acerto, 'ATIVA') RETURNING id
				""", new MapSqlParameterSource("codigo", codigo).addValue("nome", nome).addValue("fornecedor", fornecedorId)
				.addValue("entrada", entrada).addValue("acerto", acerto), Long.class);
	}

	/** Modelo novo + o "de-para" do código do fornecedor. O código interno (C0001) é gerado pelo banco. */
	public long inserirProduto(String nome, long categoriaId, BigDecimal preco, long fornecedorId, String codigoFornecedor) {
		long id = jdbc.queryForObject("""
				INSERT INTO produtos (nome, categoria_id, preco_venda, preco_custo)
				VALUES (:nome, :categoria, :preco, :preco) RETURNING id
				""", new MapSqlParameterSource("nome", nome).addValue("categoria", categoriaId).addValue("preco", preco), Long.class);
		jdbc.update("""
				INSERT INTO produto_codigos_fornecedor (produto_id, fornecedor_id, codigo_fornecedor, descricao_fornecedor)
				VALUES (:produto, :fornecedor, :codigo, :descricao)
				""", new MapSqlParameterSource("produto", id).addValue("fornecedor", fornecedorId)
				.addValue("codigo", codigoFornecedor).addValue("descricao", nome));
		return id;
	}

	public void atualizarPreco(long produtoId, BigDecimal preco) {
		jdbc.update("UPDATE produtos SET preco_venda = :preco, preco_custo = :preco WHERE id = :id",
				new MapSqlParameterSource("id", produtoId).addValue("preco", preco));
	}

	public void travarProdutos(Collection<Long> ids) {
		jdbc.queryForList("SELECT id FROM produtos WHERE id IN (:ids) ORDER BY id FOR NO KEY UPDATE",
				Map.of("ids", ids), Long.class);
	}

	/** Item da maleta + uma peça (com etiqueta própria) e uma ENTRADA no estoque para cada unidade. */
	public void inserirItemComPecas(long maletaId, long produtoId, int quantidade, BigDecimal custo,
			String codigoFornecedor, String descricao, long usuarioId) {
		MapSqlParameterSource p = new MapSqlParameterSource("maleta", maletaId).addValue("produto", produtoId)
				.addValue("qtd", quantidade).addValue("custo", custo).addValue("codigo", codigoFornecedor)
				.addValue("descricao", descricao).addValue("usuario", usuarioId);
		long item = jdbc.queryForObject("""
				INSERT INTO maleta_itens (maleta_id, produto_id, quantidade, preco_custo, codigo_fornecedor, descricao_fornecedor)
				VALUES (:maleta, :produto, :qtd, :custo, :codigo, :descricao) RETURNING id
				""", p, Long.class);
		p.addValue("item", item);
		for (int i = 0; i < quantidade; i++) {
			long peca = jdbc.queryForObject("INSERT INTO pecas (produto_id, maleta_item_id) VALUES (:produto, :item) RETURNING id",
					p, Long.class);
			Integer estoque = jdbc.queryForObject("SELECT quantidade_estoque FROM produtos WHERE id = :produto", p, Integer.class);
			p.addValue("peca", peca).addValue("anterior", estoque).addValue("posterior", estoque + 1);
			jdbc.update("""
					INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, usuario_id, tipo, quantidade,
					                                   estoque_anterior, estoque_posterior, observacao)
					VALUES (:produto, :item, :peca, :usuario, 'ENTRADA', 1, :anterior, :posterior, 'Importação de maleta (PDF)')
					""", p);
			jdbc.update("UPDATE produtos SET quantidade_estoque = :posterior WHERE id = :produto", p);
		}
	}

	public long inserirHistorico(long maletaId, long fornecedorId, long usuarioId, String arquivoNome, String hash,
			long tamanho, String metodo, String pedido, int linhasLidas, int ignoradas, int modelosNovos, int pecas,
			BigDecimal valorTotal, boolean reimportacao) {
		return jdbc.queryForObject("""
				INSERT INTO importacoes_maleta (maleta_id, fornecedor_id, usuario_id, arquivo_nome, arquivo_hash,
				    arquivo_tamanho, metodo_leitura, pedido_fornecedor, linhas_lidas, linhas_ignoradas, modelos_novos,
				    pecas_importadas, valor_total, reimportacao_confirmada)
				VALUES (:maleta, :fornecedor, :usuario, :nome, :hash, :tamanho, :metodo, :pedido, :lidas, :ignoradas,
				    :novos, :pecas, :valor, :reimportacao) RETURNING id
				""", new MapSqlParameterSource("maleta", maletaId).addValue("fornecedor", fornecedorId)
				.addValue("usuario", usuarioId).addValue("nome", arquivoNome).addValue("hash", hash)
				.addValue("tamanho", tamanho).addValue("metodo", metodo).addValue("pedido", pedido)
				.addValue("lidas", linhasLidas).addValue("ignoradas", ignoradas).addValue("novos", modelosNovos)
				.addValue("pecas", pecas).addValue("valor", valorTotal).addValue("reimportacao", reimportacao), Long.class);
	}

	public List<EtiquetaGerada> etiquetasDaMaleta(long maletaId) {
		return jdbc.query("""
				SELECT vp.codigo AS etiqueta, p.nome, mi.codigo_fornecedor, p.preco_venda
				FROM vw_pecas vp
				JOIN produtos     p  ON p.id  = vp.produto_id
				JOIN maleta_itens mi ON mi.id = vp.maleta_item_id
				WHERE vp.maleta_id = :m
				ORDER BY p.codigo, vp.numero
				""", Map.of("m", maletaId), (rs, i) -> new EtiquetaGerada(rs.getString("etiqueta"), rs.getString("nome"),
				rs.getString("codigo_fornecedor"), rs.getBigDecimal("preco_venda").setScale(2, RoundingMode.HALF_UP)));
	}

	public Optional<Long> fornecedorPorNome(String nome) {
		return jdbc.queryForList("SELECT id FROM fornecedores WHERE lower(nome) = lower(:n)", Map.of("n", nome.trim()), Long.class)
				.stream().findFirst();
	}

}
