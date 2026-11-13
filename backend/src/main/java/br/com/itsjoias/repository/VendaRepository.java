package br.com.itsjoias.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.itsjoias.dto.VendaDTO.FormaPagamento;
import br.com.itsjoias.dto.VendaDTO.ItemVendido;
import br.com.itsjoias.dto.VendaDTO.PagamentoDetalhe;
import br.com.itsjoias.dto.VendaDTO.ParcelaDetalhe;
import br.com.itsjoias.dto.VendaDTO.VendaDetalhe;
import br.com.itsjoias.dto.VendaDTO.PecaResumo;
import br.com.itsjoias.dto.VendaDTO.VendaResumo;

/**
 * Acesso ao banco para vendas. SQL direto (JdbcTemplate): a venda mexe em seis tabelas de uma vez
 * (vendas, itens_venda, movimentacoes_estoque, produtos, parcelas, pagamentos) e as regras que
 * "fecham a conta" ficam nas triggers do banco, conferidas no COMMIT.
 */
@Repository
public class VendaRepository {

	/** Peça com tudo o que a venda precisa saber dela. */
	public record PecaParaVenda(
			long pecaId,
			String etiqueta,
			long produtoId,
			long maletaItemId,
			String situacao,
			String produtoNome,
			String produtoStatus,
			BigDecimal precoVenda,
			BigDecimal precoCusto,
			String categoria,
			String maleta) {
	}

	private static final String SELECT_PECA = """
			SELECT vp.peca_id, vp.codigo AS etiqueta, vp.produto_id, vp.maleta_item_id, vp.situacao,
			       p.nome AS produto_nome, p.status AS produto_status, p.preco_venda,
			       mi.preco_custo, c.nome AS categoria, m.nome AS maleta
			FROM vw_pecas vp
			JOIN produtos     p  ON p.id  = vp.produto_id
			JOIN categorias   c  ON c.id  = p.categoria_id
			JOIN maleta_itens mi ON mi.id = vp.maleta_item_id
			JOIN maletas      m  ON m.id  = vp.maleta_id
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public VendaRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ---- encontrar peças -----------------------------------------------------------------

	/** Etiqueta (C0001-02, ou o código antigo do produto) -> peça. */
	public Optional<Long> pecaPorEtiqueta(String etiqueta) {
		return jdbc.queryForList("SELECT peca_id FROM vw_busca_peca WHERE codigo = :c", Map.of("c", etiqueta), Long.class)
				.stream().findFirst();
	}

	/** Código do modelo (C0001, ou o código antigo) -> produto. */
	public Optional<Long> produtoPorCodigo(String codigo) {
		return jdbc.queryForList("""
				SELECT produto_id FROM vw_busca_codigo
				WHERE codigo = :c AND origem IN ('INTERNO', 'INTERNO_ANTIGO')
				""", Map.of("c", codigo), Long.class).stream().findFirst();
	}

	/** Código de barras da etiqueta do fornecedor -> modelos (normalmente um só). */
	public List<String> produtosPorCodigoFornecedor(String codigo) {
		return jdbc.queryForList("""
				SELECT DISTINCT p.id || ':' || p.codigo FROM produto_codigos_fornecedor f
				JOIN produtos p ON p.id = f.produto_id
				WHERE f.codigo_fornecedor = :c ORDER BY 1
				""", Map.of("c", codigo), String.class);
	}

	public Optional<Long> produtoDaPeca(long pecaId) {
		return jdbc.queryForList("SELECT produto_id FROM pecas WHERE id = :id", Map.of("id", pecaId), Long.class)
				.stream().findFirst();
	}

	public PecaParaVenda peca(long pecaId) {
		return jdbc.queryForObject(SELECT_PECA + " WHERE vp.peca_id = :id", Map.of("id", pecaId), (rs, i) -> mapearPeca(rs));
	}

	/**
	 * Vender pelo modelo: a peça DISPONÍVEL da maleta mais antiga (FIFO), sem repetir as já escolhidas.
	 */
	public Optional<Long> primeiraPecaDisponivel(long produtoId, Collection<Long> jaEscolhidas) {
		MapSqlParameterSource p = new MapSqlParameterSource("produto", produtoId)
				.addValue("escolhidas", jaEscolhidas.isEmpty() ? List.of(-1L) : jaEscolhidas);
		return jdbc.queryForList("""
				SELECT peca_id FROM vw_pecas
				WHERE produto_id = :produto AND situacao = 'DISPONIVEL' AND peca_id NOT IN (:escolhidas)
				ORDER BY maleta_data_entrada, maleta_id, numero
				LIMIT 1
				""", p, Long.class).stream().findFirst();
	}

	/**
	 * Trava os produtos (sempre na mesma ordem, por id). Duas vendas ao mesmo tempo nunca escolhem
	 * a mesma peça; é a mesma trava usada pelas triggers do banco, então não há deadlock.
	 */
	public void travarProdutos(Collection<Long> produtoIds) {
		jdbc.queryForList("SELECT id FROM produtos WHERE id IN (:ids) ORDER BY id FOR NO KEY UPDATE",
				Map.of("ids", produtoIds), Long.class);
	}

	// ---- gravar ---------------------------------------------------------------------------

	public long inserirVenda(long clienteId, long usuarioId, LocalDate data, BigDecimal valorProdutos, BigDecimal acrescimo) {
		return jdbc.queryForObject("""
				INSERT INTO vendas (cliente_id, usuario_id, data_venda, valor_produtos, desconto, acrescimo,
				                    status, finalizada_em)
				VALUES (:cliente, :usuario, :data, :produtos, 0, :acrescimo, 'FINALIZADA', now())
				RETURNING id
				""", new MapSqlParameterSource("cliente", clienteId).addValue("usuario", usuarioId)
				.addValue("data", data).addValue("produtos", valorProdutos).addValue("acrescimo", acrescimo), Long.class);
	}

	/** Item da venda (preço e custo congelados) + baixa da peça no estoque (VENDA). */
	public void inserirItemEBaixa(long vendaId, long usuarioId, PecaParaVenda peca) {
		MapSqlParameterSource p = new MapSqlParameterSource("venda", vendaId).addValue("usuario", usuarioId)
				.addValue("produto", peca.produtoId()).addValue("maletaItem", peca.maletaItemId())
				.addValue("peca", peca.pecaId()).addValue("preco", peca.precoVenda()).addValue("custo", peca.precoCusto());
		jdbc.update("""
				INSERT INTO itens_venda (venda_id, produto_id, maleta_item_id, peca_id, preco_unitario, preco_custo_unitario)
				VALUES (:venda, :produto, :maletaItem, :peca, :preco, :custo)
				""", p);
		Integer estoque = jdbc.queryForObject("SELECT quantidade_estoque FROM produtos WHERE id = :produto", p, Integer.class);
		p.addValue("anterior", estoque).addValue("posterior", estoque - 1);
		jdbc.update("""
				INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, venda_id, usuario_id, tipo,
				                                   quantidade, estoque_anterior, estoque_posterior)
				VALUES (:produto, :maletaItem, :peca, :venda, :usuario, 'VENDA', -1, :anterior, :posterior)
				""", p);
		jdbc.update("UPDATE produtos SET quantidade_estoque = :posterior WHERE id = :produto", p);
	}

	public long inserirParcela(long vendaId, int numero, BigDecimal valor, LocalDate vencimento) {
		return jdbc.queryForObject("""
				INSERT INTO parcelas (venda_id, numero_parcela, valor, data_vencimento, status)
				VALUES (:venda, :numero, :valor, :vencimento, 'ATIVA') RETURNING id
				""", new MapSqlParameterSource("venda", vendaId).addValue("numero", numero)
				.addValue("valor", valor).addValue("vencimento", vencimento), Long.class);
	}

	public void inserirPagamento(long parcelaId, long usuarioId, BigDecimal valor, LocalDate data, FormaPagamento forma) {
		jdbc.update("""
				INSERT INTO pagamentos (parcela_id, usuario_id, valor, data_pagamento, forma_pagamento, observacao)
				VALUES (:parcela, :usuario, :valor, :data, :forma, 'Pago no ato da venda')
				""", new MapSqlParameterSource("parcela", parcelaId).addValue("usuario", usuarioId)
				.addValue("valor", valor).addValue("data", data).addValue("forma", forma.name()));
	}

	// ---- cancelar ------------------------------------------------------------------------

	/** Trava a venda (um pagamento ou outro cancelamento ao mesmo tempo espera) e devolve a situação. */
	public Optional<String> travarVenda(long vendaId) {
		return jdbc.queryForList("SELECT status FROM vendas WHERE id = :id FOR UPDATE", Map.of("id", vendaId), String.class)
				.stream().findFirst();
	}

	public record PecaVendida(long pecaId, long produtoId, long maletaItemId) {
	}

	public List<PecaVendida> pecasDaVenda(long vendaId) {
		return jdbc.query("SELECT peca_id, produto_id, maleta_item_id FROM itens_venda WHERE venda_id = :v ORDER BY id",
				Map.of("v", vendaId), (rs, i) -> new PecaVendida(rs.getLong("peca_id"), rs.getLong("produto_id"),
						rs.getLong("maleta_item_id")));
	}

	/** Pagamentos não são apagados: ficam marcados como estornados, com quem e por quê. */
	public int estornarPagamentos(long vendaId, long usuarioId, String motivo) {
		return jdbc.update("""
				UPDATE pagamentos SET estornado = true, estornado_em = now(), estornado_por = :usuario, motivo_estorno = :motivo
				WHERE NOT estornado AND parcela_id IN (SELECT id FROM parcelas WHERE venda_id = :venda)
				""", new MapSqlParameterSource("venda", vendaId).addValue("usuario", usuarioId).addValue("motivo", motivo));
	}

	public int cancelarParcelas(long vendaId) {
		return jdbc.update("UPDATE parcelas SET status = 'CANCELADA' WHERE venda_id = :v AND status = 'ATIVA'",
				Map.of("v", vendaId));
	}

	/** A peça volta a ficar disponível na mesma maleta (CANCELAMENTO_VENDA desta venda). */
	public void devolverPeca(long vendaId, long usuarioId, PecaVendida peca) {
		MapSqlParameterSource p = new MapSqlParameterSource("venda", vendaId).addValue("usuario", usuarioId)
				.addValue("produto", peca.produtoId()).addValue("maletaItem", peca.maletaItemId()).addValue("peca", peca.pecaId());
		Integer estoque = jdbc.queryForObject("SELECT quantidade_estoque FROM produtos WHERE id = :produto", p, Integer.class);
		p.addValue("anterior", estoque).addValue("posterior", estoque + 1);
		jdbc.update("""
				INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, venda_id, usuario_id, tipo,
				                                   quantidade, estoque_anterior, estoque_posterior, observacao)
				VALUES (:produto, :maletaItem, :peca, :venda, :usuario, 'CANCELAMENTO_VENDA', 1, :anterior, :posterior,
				        'Venda cancelada')
				""", p);
		jdbc.update("UPDATE produtos SET quantidade_estoque = :posterior WHERE id = :produto", p);
	}

	public void marcarCancelada(long vendaId, String motivo) {
		jdbc.update("UPDATE vendas SET status = 'CANCELADA', cancelada_em = now(), motivo_cancelamento = :motivo WHERE id = :id",
				new MapSqlParameterSource("id", vendaId).addValue("motivo", motivo));
	}

	// ---- consultas -----------------------------------------------------------------------

	public Optional<VendaDetalhe> detalhe(long vendaId) {
		Map<String, Object> id = Map.of("id", vendaId);
		List<ItemVendido> itens = jdbc.query("""
				SELECT vp.codigo AS etiqueta, p.nome AS produto, m.nome AS maleta, iv.preco_unitario
				FROM itens_venda iv
				JOIN vw_pecas  vp ON vp.peca_id = iv.peca_id
				JOIN produtos  p  ON p.id = iv.produto_id
				JOIN maletas   m  ON m.id = vp.maleta_id
				WHERE iv.venda_id = :id ORDER BY iv.id
				""", id, (rs, i) -> new ItemVendido(rs.getString("etiqueta"), rs.getString("produto"), rs.getString("maleta"),
						dinheiro(rs, "preco_unitario")));
		List<ParcelaDetalhe> parcelas = jdbc.query("""
				SELECT pa.numero_parcela, pa.data_vencimento, pa.valor, pa.status,
				       COALESCE((SELECT SUM(pg.valor) FROM pagamentos pg WHERE pg.parcela_id = pa.id AND NOT pg.estornado), 0) AS pago
				FROM parcelas pa WHERE pa.venda_id = :id ORDER BY pa.numero_parcela
				""", id, (rs, i) -> new ParcelaDetalhe(rs.getInt("numero_parcela"), rs.getObject("data_vencimento", LocalDate.class),
						dinheiro(rs, "valor"), dinheiro(rs, "pago"), rs.getString("status")));
		List<PagamentoDetalhe> pagamentos = jdbc.query("""
				SELECT pg.data_pagamento, pg.valor, pg.forma_pagamento, pg.estornado, pg.motivo_estorno
				FROM pagamentos pg JOIN parcelas pa ON pa.id = pg.parcela_id
				WHERE pa.venda_id = :id ORDER BY pg.data_pagamento, pg.id
				""", id, (rs, i) -> new PagamentoDetalhe(rs.getObject("data_pagamento", LocalDate.class), dinheiro(rs, "valor"),
						rs.getString("forma_pagamento"), rs.getBoolean("estornado"), rs.getString("motivo_estorno")));
		return jdbc.query("""
				SELECT v.id, v.data_venda, v.cliente_id, c.nome AS cliente_nome, v.status, u.nome AS vendida_por,
				       v.valor_produtos, v.acrescimo, v.valor_total, v.cancelada_em, v.motivo_cancelamento
				FROM vendas v JOIN clientes c ON c.id = v.cliente_id LEFT JOIN usuarios u ON u.id = v.usuario_id
				WHERE v.id = :id AND v.status <> 'RASCUNHO'
				""", id, (rs, i) -> new VendaDetalhe(rs.getLong("id"), rs.getObject("data_venda", LocalDate.class),
						rs.getLong("cliente_id"), rs.getString("cliente_nome"), rs.getString("status"), rs.getString("vendida_por"),
						dinheiro(rs, "valor_produtos"), dinheiro(rs, "acrescimo"), dinheiro(rs, "valor_total"),
						rs.getObject("cancelada_em", java.time.OffsetDateTime.class), rs.getString("motivo_cancelamento"),
						itens, parcelas, pagamentos)).stream().findFirst();
	}

	public Optional<String> nomeClienteAtiva(long clienteId) {
		return jdbc.queryForList("SELECT nome FROM clientes WHERE id = :id AND ativo", Map.of("id", clienteId), String.class)
				.stream().findFirst();
	}

	public List<VendaResumo> listarRecentes(Long clienteId, int limite) {
		Map<String, Object> p = new HashMap<>();
		p.put("cliente", clienteId);
		p.put("limite", limite);
		return jdbc.query("""
				SELECT v.id, v.data_venda, v.cliente_id, c.nome AS cliente_nome, v.valor_total, v.status,
				       (SELECT COUNT(*) FROM itens_venda iv WHERE iv.venda_id = v.id)              AS pecas,
				       COALESCE(f.pago, 0) AS pago, COALESCE(f.saldo, 0) AS saldo, COALESCE(f.parcelas, 0) AS parcelas
				FROM vendas v
				JOIN clientes c ON c.id = v.cliente_id
				LEFT JOIN (SELECT venda_id, SUM(total_pago) AS pago, SUM(saldo) AS saldo, COUNT(*) AS parcelas
				           FROM vw_saldo_parcelas GROUP BY venda_id) f ON f.venda_id = v.id
				WHERE v.status <> 'RASCUNHO' AND (CAST(:cliente AS bigint) IS NULL OR v.cliente_id = :cliente)
				ORDER BY v.data_venda DESC, v.id DESC
				LIMIT :limite
				""", p, (rs, i) -> new VendaResumo(
				rs.getLong("id"), rs.getObject("data_venda", LocalDate.class), rs.getLong("cliente_id"),
				rs.getString("cliente_nome"), rs.getInt("pecas"), dinheiro(rs, "valor_total"), dinheiro(rs, "pago"),
				dinheiro(rs, "saldo"), rs.getInt("parcelas"), rs.getString("status")));
	}

	/** Busca de peças por etiqueta, código do modelo ou nome. situacao = null traz todas. */
	public List<PecaResumo> buscarPecas(String busca, String situacao, int limite) {
		MapSqlParameterSource p = new MapSqlParameterSource("limite", limite).addValue("situacao", situacao);
		StringBuilder sql = new StringBuilder("""
				SELECT vp.peca_id, vp.codigo AS etiqueta, p.codigo AS produto_codigo, p.nome AS produto,
				       c.nome AS categoria, m.nome AS maleta, p.preco_venda, vp.situacao
				FROM vw_pecas vp
				JOIN produtos   p ON p.id = vp.produto_id
				JOIN categorias c ON c.id = p.categoria_id
				JOIN maletas    m ON m.id = vp.maleta_id
				WHERE (CAST(:situacao AS text) IS NULL OR vp.situacao = :situacao)
				""");
		if (busca != null && !busca.isBlank()) {
			// etiqueta (C0001-02), modelo (C0001), nome ou código de barras da etiqueta do fornecedor
			sql.append(" AND (vp.codigo LIKE :codigo OR lower(p.nome) LIKE :nome OR EXISTS (SELECT 1 FROM produto_codigos_fornecedor f"
					+ " WHERE f.produto_id = p.id AND f.codigo_fornecedor LIKE :codigo))");
			p.addValue("codigo", busca.trim().toUpperCase().replace("%", "") + "%");
			p.addValue("nome", "%" + busca.trim().toLowerCase().replace("%", "") + "%");
		}
		sql.append(" ORDER BY p.codigo, vp.maleta_data_entrada, vp.maleta_id, vp.numero LIMIT :limite");
		return jdbc.query(sql.toString(), p, (rs, i) -> new PecaResumo(rs.getLong("peca_id"), rs.getString("etiqueta"),
				rs.getString("produto_codigo"), rs.getString("produto"), rs.getString("categoria"), rs.getString("maleta"),
				dinheiro(rs, "preco_venda"), rs.getString("situacao")));
	}

	private static PecaParaVenda mapearPeca(ResultSet rs) throws SQLException {
		return new PecaParaVenda(rs.getLong("peca_id"), rs.getString("etiqueta"), rs.getLong("produto_id"),
				rs.getLong("maleta_item_id"), rs.getString("situacao"), rs.getString("produto_nome"),
				rs.getString("produto_status"), dinheiro(rs, "preco_venda"), dinheiro(rs, "preco_custo"),
				rs.getString("categoria"), rs.getString("maleta"));
	}

	private static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
		BigDecimal v = rs.getBigDecimal(coluna);
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
	}

}
