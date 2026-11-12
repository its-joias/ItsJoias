package br.com.itsjoias.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.itsjoias.dto.ClienteDTO.ClienteResposta;

/**
 * Leitura de clientes junto com o saldo (vw_saldo_clientes), numa consulta só.
 * A gravação continua pela entidade Cliente (ClienteRepository, JPA).
 */
@Repository
public class ClienteConsultaRepository {

	private static final String SELECT = """
			SELECT c.id, c.nome, c.telefone, c.email, c.cpf, c.endereco, c.observacoes, c.ativo,
			       s.qtd_vendas, s.total_comprado, s.total_em_aberto, s.total_atrasado, s.maior_dias_atraso
			FROM clientes c
			JOIN vw_saldo_clientes s ON s.cliente_id = c.id
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public ClienteConsultaRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Lista por nome. A busca procura no nome (sem diferenciar maiúsculas) e nos dígitos do telefone.
	 */
	public List<ClienteResposta> listar(String busca, boolean incluirInativos) {
		Map<String, Object> p = new HashMap<>();
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE (CAST(:todos AS boolean) OR c.ativo)");
		p.put("todos", incluirInativos);
		if (busca != null && !busca.isBlank()) {
			String digitos = busca.replaceAll("\\D", "");
			sql.append(" AND (lower(c.nome) LIKE :nome");
			p.put("nome", "%" + busca.trim().toLowerCase().replace("%", "") + "%");
			if (!digitos.isEmpty()) {
				sql.append(" OR regexp_replace(c.telefone, '[^0-9]', '', 'g') LIKE :digitos");
				p.put("digitos", "%" + digitos + "%");
			}
			sql.append(")");
		}
		sql.append(" ORDER BY lower(c.nome), c.id LIMIT 500");
		return jdbc.query(sql.toString(), p, (rs, i) -> mapear(rs));
	}

	public Optional<ClienteResposta> buscar(long id) {
		return jdbc.query(SELECT + " WHERE c.id = :id", Map.of("id", id), (rs, i) -> mapear(rs))
				.stream().findFirst();
	}

	/** Tem venda ou cobrança registrada? (aí não pode ser excluída, só desativada) */
	public boolean temHistorico(long id) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM vendas WHERE cliente_id = :id)
				    OR EXISTS (SELECT 1 FROM cobrancas WHERE cliente_id = :id)
				""", Map.of("id", id), Boolean.class));
	}

	private static ClienteResposta mapear(ResultSet rs) throws SQLException {
		return new ClienteResposta(
				rs.getLong("id"),
				rs.getString("nome"),
				rs.getString("telefone"),
				rs.getString("email"),
				rs.getString("cpf"),
				rs.getString("endereco"),
				rs.getString("observacoes"),
				rs.getBoolean("ativo"),
				rs.getLong("qtd_vendas"),
				dinheiro(rs, "total_comprado"),
				dinheiro(rs, "total_em_aberto"),
				dinheiro(rs, "total_atrasado"),
				rs.getInt("maior_dias_atraso"));
	}

	private static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
		BigDecimal v = rs.getBigDecimal(coluna);
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
	}

}
