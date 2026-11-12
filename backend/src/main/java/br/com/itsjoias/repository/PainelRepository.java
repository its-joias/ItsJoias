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

import br.com.itsjoias.dto.PainelDTO.AReceber;
import br.com.itsjoias.dto.PainelDTO.Atrasado;
import br.com.itsjoias.dto.PainelDTO.ClientesDevendo;
import br.com.itsjoias.dto.PainelDTO.MaletaAtiva;
import br.com.itsjoias.dto.PainelDTO.ParcelaAVencer;
import br.com.itsjoias.dto.PainelDTO.ParcelaAtrasada;
import br.com.itsjoias.dto.PainelDTO.ProximoDia5;
import br.com.itsjoias.dto.PainelDTO.Recebido;
import br.com.itsjoias.dto.PainelDTO.SituacaoParcela;
import br.com.itsjoias.dto.PainelDTO.Totais;
import br.com.itsjoias.dto.PainelDTO.TotalVendido;

/**
 * Consultas do Painel. Toda soma é feita no banco, em consultas agregadas (uma por bloco).
 *
 * O painel não tem entidade própria, então usa SQL direto (JdbcTemplate) em vez de JpaRepository.
 * As regras de parcela vêm da view vw_saldo_parcelas (só venda FINALIZADA e parcela ATIVA,
 * pago = pagamentos não estornados, saldo, situação PENDENTE/PARCIAL/PAGA), e a situação de cada
 * peça vem de vw_pecas (última movimentação). O atraso NÃO usa o CURRENT_DATE das views:
 * "hoje" chega como parâmetro, já calculado em São Paulo pelo back.
 */
@Repository
public class PainelRepository {

	private static final String SQL_TOTAIS = """
			WITH vendido AS (
			    SELECT COALESCE(SUM(valor_total), 0) AS total, COUNT(*) AS qtd
			    FROM vendas
			    WHERE status = 'FINALIZADA'
			), parcelas AS (
			    SELECT  COALESCE(SUM(total_pago), 0)                                          AS recebido,
			            COUNT(*)                   FILTER (WHERE saldo > 0)                    AS qtd_abertas,
			            COALESCE(SUM(saldo)        FILTER (WHERE saldo > 0 AND data_vencimento < CAST(:hoje AS date)), 0) AS atrasado,
			            COUNT(*)                   FILTER (WHERE saldo > 0 AND data_vencimento < CAST(:hoje AS date))     AS qtd_atrasadas,
			            COUNT(DISTINCT cliente_id) FILTER (WHERE saldo > 0)                    AS clientes_devendo,
			            COUNT(DISTINCT cliente_id) FILTER (WHERE saldo > 0 AND data_vencimento < CAST(:hoje AS date))     AS clientes_atrasados,
			            COALESCE(SUM(saldo)        FILTER (WHERE saldo > 0 AND data_vencimento = CAST(:dia5 AS date)), 0) AS dia5_valor,
			            COUNT(*)                   FILTER (WHERE saldo > 0 AND data_vencimento = CAST(:dia5 AS date))     AS dia5_qtd
			    FROM vw_saldo_parcelas
			)
			SELECT  v.total                                           AS total_vendido,
			        v.qtd                                             AS qtd_vendas,
			        p.recebido,
			        ROUND(p.recebido * 100 / NULLIF(v.total, 0), 1)  AS percentual_recebido,
			        v.total - p.recebido                              AS a_receber,
			        p.qtd_abertas,
			        p.atrasado,
			        p.qtd_atrasadas,
			        p.clientes_devendo,
			        p.clientes_atrasados,
			        p.dia5_valor,
			        p.dia5_qtd
			FROM vendido v CROSS JOIN parcelas p
			""";

	// total de parcelas = parcelas ATIVAS da venda (as canceladas não entram no "2/3")
	private static final String SQL_PARCELAS_ATRASADAS = """
			SELECT  sp.parcela_id, sp.venda_id, sp.cliente_id, c.nome AS cliente_nome,
			        sp.numero_parcela,
			        (SELECT COUNT(*) FROM parcelas p2
			          WHERE p2.venda_id = sp.venda_id AND p2.status = 'ATIVA') AS total_parcelas,
			        sp.data_vencimento, sp.valor_parcela, sp.total_pago, sp.saldo, sp.situacao,
			        (CAST(:hoje AS date) - sp.data_vencimento)                               AS dias_atraso
			FROM vw_saldo_parcelas sp
			JOIN clientes c ON c.id = sp.cliente_id
			WHERE sp.saldo > 0
			  AND sp.data_vencimento < CAST(:hoje AS date)
			ORDER BY sp.data_vencimento, c.nome, sp.venda_id, sp.numero_parcela
			""";

	private static final String SQL_VENCEM_NO_DIA = """
			SELECT  sp.parcela_id, sp.venda_id, sp.cliente_id, c.nome AS cliente_nome,
			        sp.numero_parcela,
			        (SELECT COUNT(*) FROM parcelas p2
			          WHERE p2.venda_id = sp.venda_id AND p2.status = 'ATIVA') AS total_parcelas,
			        sp.data_vencimento, sp.valor_parcela, sp.saldo, sp.situacao
			FROM vw_saldo_parcelas sp
			JOIN clientes c ON c.id = sp.cliente_id
			WHERE sp.saldo > 0
			  AND sp.data_vencimento = CAST(:dia AS date)
			ORDER BY c.nome, sp.venda_id, sp.numero_parcela
			""";

	private static final String SQL_MALETAS_ATIVAS = """
			WITH ativas AS (
			    SELECT m.id, m.codigo, m.nome, m.data_entrada, f.nome AS fornecedor
			    FROM maletas m
			    JOIN fornecedores f ON f.id = m.fornecedor_id
			    WHERE m.status = 'ATIVA'
			), pecas AS (
			    SELECT  vp.maleta_id,
			            COUNT(*)                                          AS recebidas,
			            COUNT(*) FILTER (WHERE vp.situacao = 'VENDIDA')    AS vendidas,
			            COUNT(*) FILTER (WHERE vp.situacao = 'DISPONIVEL') AS disponiveis,
			            COUNT(*) FILTER (WHERE vp.situacao = 'PERDIDA')    AS perdidas,
			            COUNT(*) FILTER (WHERE vp.situacao = 'DEVOLVIDA')  AS devolvidas
			    FROM vw_pecas vp
			    WHERE vp.maleta_id IN (SELECT id FROM ativas)
			    GROUP BY vp.maleta_id
			), custo AS (
			    SELECT maleta_id, SUM(quantidade * preco_custo) AS custo_total
			    FROM maleta_itens
			    WHERE maleta_id IN (SELECT id FROM ativas)
			    GROUP BY maleta_id
			), vendido AS (
			    SELECT mi.maleta_id, SUM(iv.preco_unitario) AS total_vendido
			    FROM itens_venda iv
			    JOIN vendas       v  ON v.id  = iv.venda_id AND v.status = 'FINALIZADA'
			    JOIN maleta_itens mi ON mi.id = iv.maleta_item_id
			    WHERE mi.maleta_id IN (SELECT id FROM ativas)
			    GROUP BY mi.maleta_id
			)
			SELECT  a.id, a.codigo, a.nome, a.fornecedor, a.data_entrada,
			        COALESCE(p.recebidas, 0)    AS recebidas,
			        COALESCE(p.vendidas, 0)     AS vendidas,
			        COALESCE(p.disponiveis, 0)  AS disponiveis,
			        COALESCE(p.perdidas, 0)     AS perdidas,
			        COALESCE(p.devolvidas, 0)   AS devolvidas,
			        COALESCE(c.custo_total, 0)  AS custo_total,
			        COALESCE(vd.total_vendido, 0) AS total_vendido
			FROM ativas a
			LEFT JOIN pecas   p  ON p.maleta_id  = a.id
			LEFT JOIN custo   c  ON c.maleta_id  = a.id
			LEFT JOIN vendido vd ON vd.maleta_id = a.id
			ORDER BY a.data_entrada, a.id
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public PainelRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Totais buscarTotais(LocalDate hoje, LocalDate proximoDia5) {
		return jdbc.queryForObject(SQL_TOTAIS, Map.of("hoje", hoje, "dia5", proximoDia5),
				(rs, i) -> new Totais(
						new TotalVendido(dinheiro(rs, "total_vendido"), rs.getLong("qtd_vendas")),
						new Recebido(dinheiro(rs, "recebido"), rs.getBigDecimal("percentual_recebido")),
						new AReceber(dinheiro(rs, "a_receber"), rs.getLong("qtd_abertas")),
						new Atrasado(dinheiro(rs, "atrasado"), rs.getLong("qtd_atrasadas")),
						new ClientesDevendo(rs.getLong("clientes_devendo"), rs.getLong("clientes_atrasados")),
						new ProximoDia5(proximoDia5, dinheiro(rs, "dia5_valor"), rs.getLong("dia5_qtd"))));
	}

	public List<ParcelaAtrasada> buscarParcelasAtrasadas(LocalDate hoje) {
		return jdbc.query(SQL_PARCELAS_ATRASADAS, Map.of("hoje", hoje),
				(rs, i) -> new ParcelaAtrasada(
						rs.getLong("parcela_id"),
						rs.getLong("venda_id"),
						rs.getLong("cliente_id"),
						rs.getString("cliente_nome"),
						rs.getInt("numero_parcela"),
						rs.getInt("total_parcelas"),
						rs.getObject("data_vencimento", LocalDate.class),
						dinheiro(rs, "valor_parcela"),
						dinheiro(rs, "total_pago"),
						dinheiro(rs, "saldo"),
						SituacaoParcela.valueOf(rs.getString("situacao")),
						rs.getInt("dias_atraso")));
	}

	public List<ParcelaAVencer> buscarParcelasQueVencemEm(LocalDate dia) {
		return jdbc.query(SQL_VENCEM_NO_DIA, Map.of("dia", dia),
				(rs, i) -> new ParcelaAVencer(
						rs.getLong("parcela_id"),
						rs.getLong("venda_id"),
						rs.getLong("cliente_id"),
						rs.getString("cliente_nome"),
						rs.getInt("numero_parcela"),
						rs.getInt("total_parcelas"),
						rs.getObject("data_vencimento", LocalDate.class),
						dinheiro(rs, "valor_parcela"),
						dinheiro(rs, "saldo"),
						SituacaoParcela.valueOf(rs.getString("situacao"))));
	}

	public List<MaletaAtiva> buscarMaletasAtivas() {
		return jdbc.query(SQL_MALETAS_ATIVAS, Map.of(),
				(rs, i) -> {
					int perdidas = rs.getInt("perdidas");
					int devolvidas = rs.getInt("devolvidas");
					return new MaletaAtiva(
							rs.getLong("id"),
							rs.getString("codigo"),
							rs.getString("nome"),
							rs.getString("fornecedor"),
							rs.getObject("data_entrada", LocalDate.class),
							rs.getInt("recebidas"),
							rs.getInt("vendidas"),
							rs.getInt("disponiveis"),
							perdidas,
							devolvidas,
							perdidas + devolvidas,
							dinheiro(rs, "custo_total"),
							dinheiro(rs, "total_vendido"));
				});
	}

	/** numeric do banco -> BigDecimal sempre com 2 casas (o COALESCE(..., 0) volta sem casas). */
	private static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
		BigDecimal valor = rs.getBigDecimal(coluna);
		return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
	}

}
