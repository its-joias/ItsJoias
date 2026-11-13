package br.com.itsjoias.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.itsjoias.dto.GraficoDTO.PecasDaCategoria;
import br.com.itsjoias.dto.GraficoDTO.RecebimentosDoMes;
import br.com.itsjoias.dto.GraficoDTO.VendasDoMes;

/** Consultas dos gráficos. Mês sem movimento aparece com zero (o eixo do tempo não pula meses). */
@Repository
public class GraficoRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public GraficoRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Total vendido (vendas finalizadas; canceladas não contam) nos últimos N meses, até o mês de hoje. */
	public List<VendasDoMes> vendasPorMes(LocalDate hoje, int meses) {
		return jdbc.query("""
				SELECT to_char(m.mes, 'YYYY-MM') AS mes, COALESCE(SUM(v.valor_total), 0) AS vendido, COUNT(v.id) AS vendas
				FROM generate_series(date_trunc('month', CAST(:hoje AS date)) - make_interval(months => :meses - 1),
				                     date_trunc('month', CAST(:hoje AS date)), interval '1 month') AS m(mes)
				LEFT JOIN vendas v ON v.status = 'FINALIZADA' AND date_trunc('month', v.data_venda) = m.mes
				GROUP BY m.mes ORDER BY m.mes
				""", Map.of("hoje", hoje, "meses", meses),
				(rs, i) -> new VendasDoMes(rs.getString("mes"), dinheiro(rs, "vendido"), rs.getInt("vendas")));
	}

	/** Por mês de vencimento das parcelas: de N meses atrás até N meses à frente (as regras vêm de vw_saldo_parcelas). */
	public List<RecebimentosDoMes> recebimentosPorMes(LocalDate hoje, int mesesAntes, int mesesDepois) {
		return jdbc.query("""
				SELECT to_char(m.mes, 'YYYY-MM') AS mes,
				       COALESCE(SUM(s.total_pago), 0) AS recebido,
				       COALESCE(SUM(s.saldo), 0) AS a_receber,
				       COALESCE(SUM(s.saldo) FILTER (WHERE s.atrasada), 0) AS atrasado
				FROM generate_series(date_trunc('month', CAST(:hoje AS date)) - make_interval(months => :antes),
				                     date_trunc('month', CAST(:hoje AS date)) + make_interval(months => :depois),
				                     interval '1 month') AS m(mes)
				LEFT JOIN vw_saldo_parcelas s ON date_trunc('month', s.data_vencimento) = m.mes
				GROUP BY m.mes ORDER BY m.mes
				""", Map.of("hoje", hoje, "antes", mesesAntes, "depois", mesesDepois),
				(rs, i) -> new RecebimentosDoMes(rs.getString("mes"), dinheiro(rs, "recebido"), dinheiro(rs, "a_receber"),
						dinheiro(rs, "atrasado")));
	}

	/** Peças das maletas ATIVAS por categoria (maleta encerrada ou descartada não entra). */
	public List<PecasDaCategoria> pecasPorCategoria() {
		return jdbc.query("""
				SELECT c.nome AS categoria,
				       COUNT(*) FILTER (WHERE vp.situacao = 'DISPONIVEL')                AS disponiveis,
				       COUNT(*) FILTER (WHERE vp.situacao = 'VENDIDA')                   AS vendidas,
				       COUNT(*) FILTER (WHERE vp.situacao IN ('PERDIDA', 'DEVOLVIDA'))   AS perdidas
				FROM vw_pecas vp
				JOIN maletas    m ON m.id = vp.maleta_id AND m.status = 'ATIVA'
				JOIN produtos   p ON p.id = vp.produto_id
				JOIN categorias c ON c.id = p.categoria_id
				GROUP BY c.nome
				ORDER BY COUNT(*) DESC, c.nome
				""", Map.of(), (rs, i) -> new PecasDaCategoria(rs.getString("categoria"), rs.getInt("disponiveis"),
						rs.getInt("vendidas"), rs.getInt("perdidas")));
	}

	private static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
		BigDecimal valor = rs.getBigDecimal(coluna);
		return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
	}

}
