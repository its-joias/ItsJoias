package br.com.itsjoias.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Ações sobre uma maleta inteira (hoje: descartar uma importação errada). */
@Repository
public class MaletaRepository {

	/** Começo da observação de uma maleta descartada (a importação do mesmo PDF deixa de contar como repetida). */
	public static final String MARCA_DESCARTADA = "[Descartada] ";

	private final NamedParameterJdbcTemplate jdbc;

	public MaletaRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record MaletaTravada(long id, String codigo, String nome, String status) {
	}

	public Optional<MaletaTravada> travar(long maletaId) {
		return jdbc.query("SELECT id, codigo, nome, status FROM maletas WHERE id = :id FOR UPDATE", Map.of("id", maletaId),
				(rs, i) -> new MaletaTravada(rs.getLong("id"), rs.getString("codigo"), rs.getString("nome"), rs.getString("status")))
				.stream().findFirst();
	}

	public record PecaDaMaleta(long pecaId, long produtoId, long maletaItemId, String situacao) {
	}

	public List<PecaDaMaleta> pecas(long maletaId) {
		return jdbc.query("SELECT peca_id, produto_id, maleta_item_id, situacao FROM vw_pecas WHERE maleta_id = :m ORDER BY peca_id",
				Map.of("m", maletaId), (rs, i) -> new PecaDaMaleta(rs.getLong("peca_id"), rs.getLong("produto_id"),
						rs.getLong("maleta_item_id"), rs.getString("situacao")));
	}

	/** Mesma trava (e mesma ordem) da venda e das triggers do banco: sem deadlock. */
	public void travarProdutos(Collection<Long> produtoIds) {
		if (!produtoIds.isEmpty()) {
			jdbc.queryForList("SELECT id FROM produtos WHERE id IN (:ids) ORDER BY id FOR NO KEY UPDATE",
					Map.of("ids", produtoIds), Long.class);
		}
	}

	/** A peça sai do estoque como devolvida ao fornecedor (o histórico continua lá). */
	public void devolverAoFornecedor(long usuarioId, PecaDaMaleta peca, String observacao) {
		MapSqlParameterSource p = new MapSqlParameterSource("usuario", usuarioId).addValue("produto", peca.produtoId())
				.addValue("maletaItem", peca.maletaItemId()).addValue("peca", peca.pecaId()).addValue("obs", observacao);
		Integer estoque = jdbc.queryForObject("SELECT quantidade_estoque FROM produtos WHERE id = :produto", p, Integer.class);
		p.addValue("anterior", estoque).addValue("posterior", estoque - 1);
		jdbc.update("""
				INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, usuario_id, tipo, quantidade,
				                                   estoque_anterior, estoque_posterior, observacao)
				VALUES (:produto, :maletaItem, :peca, :usuario, 'DEVOLUCAO_FORNECEDOR', -1, :anterior, :posterior, :obs)
				""", p);
		jdbc.update("UPDATE produtos SET quantidade_estoque = :posterior WHERE id = :produto", p);
	}

	/** Encerra a maleta e libera o código dela (para importar o mesmo pedido de novo). */
	public void encerrarComoDescartada(long maletaId, String novoCodigo, String novoNome, String motivo) {
		jdbc.update("""
				UPDATE maletas SET status = 'ENCERRADA', codigo = :codigo, nome = :nome,
				       observacao = :obs || COALESCE(E'\\n' || observacao, '')
				WHERE id = :id
				""", new MapSqlParameterSource("id", maletaId).addValue("codigo", novoCodigo).addValue("nome", novoNome)
				.addValue("obs", MARCA_DESCARTADA + motivo));
	}

}
