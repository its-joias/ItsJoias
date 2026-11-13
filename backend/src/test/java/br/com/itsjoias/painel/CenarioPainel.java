package br.com.itsjoias.painel;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Monta, direto no banco de teste, um cenário pequeno e fechado (as triggers do banco aceitam).
 * Os valores esperados de cada bloco do Painel estão anotados abaixo, para hoje = 11/11/2026.
 *
 * Maleta Outubro (ATIVA, Fornecedora Teste, entrada 01/10/2026)
 *   P1 (colar): 4 peças a R$ 40,00 de custo    P2 (brinco): 3 peças a R$ 20,00 de custo
 *   custo total = 4 × 40 + 3 × 20 = R$ 220,00
 *   P1-01 vendida (V1)   P1-02 vendida (V2)   P1-03 vendida na V3 e devolvida no cancelamento
 *   P1-04 vendida (V4)   P2-01 vendida (V1)   P2-02 PERDA   P2-03 DEVOLUCAO_FORNECEDOR
 *   -> 7 recebidas, 4 vendidas, 1 disponível, 1 perdida, 1 devolvida
 *   -> vendido = 100 + 50 + 100 + 100 = R$ 350,00 (a V3, cancelada, não entra)
 * Maleta Antiga (ENCERRADA): 1 peça. Não aparece no painel.
 *
 * V1 Ana   FINALIZADA  produtos 150 + acréscimo 15 = R$ 165,00 em 3 × 55,00
 *   #1 vence 05/10  pago 55,00                    -> PAGA
 *   #2 vence 05/11  pago 20,00                    -> PARCIAL, 6 dias de atraso, saldo 35,00
 *   #3 vence 05/12  pagamento de 30,00 ESTORNADO  -> PENDENTE, saldo 55,00
 * V2 Bia   FINALIZADA  produtos 100 − desconto 10 = R$ 90,00 em 3 × 30,00
 *   #1 vence 05/10                                -> PENDENTE, 37 dias de atraso
 *   #2 vence 11/11 (hoje)                         -> PENDENTE, não está atrasada
 *   #3 vence 05/12                                -> PENDENTE
 *   #4 CANCELADA, vence 05/10, 30,00              -> fora de todas as contas
 * V3 Carla CANCELADA   R$ 100,00, parcela ATIVA vencida em 05/10 -> fora de todas as contas
 * V4 Eva   FINALIZADA  R$ 100,00 em 1 × 100,00, vence 05/12      -> PENDENTE
 *
 * Totais em 11/11/2026: vendido 355,00 (3 vendas) | recebido 75,00 (21,1%)
 *   a receber 280,00 (6 parcelas) | atrasado 65,00 (2 parcelas)
 *   clientes devendo 3, 2 com atraso | próximo dia 5 = 05/12: 185,00 (3 parcelas)
 */
class CenarioPainel {

	static final LocalDate ENTRADA_MALETA = LocalDate.of(2026, 10, 1);

	final long vendaAna;
	final long vendaBia;
	final long vendaCarla;
	final long vendaEva;
	final long maletaOutubro;
	final long maletaAntiga;

	private final JdbcTemplate jdbc;
	private final long usuario;

	private record Peca(long id, long produtoId, long maletaItemId) {
	}

	CenarioPainel(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.usuario = id("INSERT INTO usuarios (nome, email, senha_hash) VALUES ('Teste', 'teste@example.com', 'x') RETURNING id");

		long colares = id("SELECT id FROM categorias WHERE prefixo = 'C'");
		long brincos = id("SELECT id FROM categorias WHERE prefixo = 'B'");
		long fornecedor = id("INSERT INTO fornecedores (nome) VALUES ('Fornecedora Teste') RETURNING id");
		long p1 = id("INSERT INTO produtos (nome, categoria_id, preco_venda) VALUES ('Colar teste', ?, 100.00) RETURNING id", colares);
		long p2 = id("INSERT INTO produtos (nome, categoria_id, preco_venda) VALUES ('Brinco teste', ?, 50.00) RETURNING id", brincos);

		// ---- maletas e peças -------------------------------------------------
		maletaOutubro = maleta("T-OUT", "Maleta Outubro", fornecedor, ENTRADA_MALETA, "ATIVA");
		long mi1 = maletaItem(maletaOutubro, p1, 4, "40.00");
		long mi2 = maletaItem(maletaOutubro, p2, 3, "20.00");
		Peca p1n1 = peca(p1, mi1);
		Peca p1n2 = peca(p1, mi1);
		Peca p1n3 = peca(p1, mi1);
		Peca p1n4 = peca(p1, mi1);
		Peca p2n1 = peca(p2, mi2);
		Peca p2n2 = peca(p2, mi2);
		Peca p2n3 = peca(p2, mi2);
		movimentar(p2n2, null, "PERDA");
		movimentar(p2n3, null, "DEVOLUCAO_FORNECEDOR");

		maletaAntiga = maleta("T-ANT", "Maleta Antiga", fornecedor, LocalDate.of(2026, 8, 1), "ENCERRADA");
		peca(p2, maletaItem(maletaAntiga, p2, 1, "20.00"));

		// ---- clientes ---------------------------------------------------------
		long ana = cliente("Ana Teste", "(71) 90000-0001");
		long bia = cliente("Bia Teste", "(71) 90000-0002");
		long carla = cliente("Carla Teste", "(71) 90000-0003");
		long eva = cliente("Eva Teste", "(71) 90000-0004");

		// ---- V1 Ana: 150 + 15 de acréscimo = 165, em 3 × 55 ------------------
		vendaAna = venda(ana, LocalDate.of(2026, 9, 20), "150.00", "0.00", "15.00", "FINALIZADA");
		vender(vendaAna, p1n1, "100.00", "40.00");
		vender(vendaAna, p2n1, "50.00", "20.00");
		long ana1 = parcela(vendaAna, 1, "55.00", LocalDate.of(2026, 10, 5), "ATIVA");
		long ana2 = parcela(vendaAna, 2, "55.00", LocalDate.of(2026, 11, 5), "ATIVA");
		long ana3 = parcela(vendaAna, 3, "55.00", LocalDate.of(2026, 12, 5), "ATIVA");
		pagar(ana1, "55.00");
		pagar(ana2, "20.00");
		estornar(pagar(ana3, "30.00"));

		// ---- V2 Bia: 100 − 10 de desconto = 90, em 3 × 30 (+ 1 parcela cancelada) --
		vendaBia = venda(bia, LocalDate.of(2026, 9, 25), "100.00", "10.00", "0.00", "FINALIZADA");
		vender(vendaBia, p1n2, "100.00", "40.00");
		parcela(vendaBia, 1, "30.00", LocalDate.of(2026, 10, 5), "ATIVA");
		parcela(vendaBia, 2, "30.00", LocalDate.of(2026, 11, 11), "ATIVA");
		parcela(vendaBia, 3, "30.00", LocalDate.of(2026, 12, 5), "ATIVA");
		parcela(vendaBia, 4, "30.00", LocalDate.of(2026, 10, 5), "CANCELADA");

		// ---- V3 Carla: cancelada, peça devolvida à maleta -------------------
		vendaCarla = venda(carla, LocalDate.of(2026, 10, 2), "100.00", "0.00", "0.00", "CANCELADA");
		vender(vendaCarla, p1n3, "100.00", "40.00");
		movimentar(p1n3, vendaCarla, "CANCELAMENTO_VENDA");
		parcela(vendaCarla, 1, "100.00", LocalDate.of(2026, 10, 5), "ATIVA");

		// ---- V4 Eva: 100 em 1 parcela que vence no dia 5 de dezembro ---------
		vendaEva = venda(eva, LocalDate.of(2026, 11, 1), "100.00", "0.00", "0.00", "FINALIZADA");
		vender(vendaEva, p1n4, "100.00", "40.00");
		parcela(vendaEva, 1, "100.00", LocalDate.of(2026, 12, 5), "ATIVA");
	}

	// ------------------------------------------------------------------------

	private long maleta(String codigo, String nome, long fornecedor, LocalDate entrada, String status) {
		return id("INSERT INTO maletas (codigo, nome, fornecedor_id, tipo, data_entrada, status) "
				+ "VALUES (?, ?, ?, 'NOVA', ?, ?) RETURNING id", codigo, nome, fornecedor, entrada, status);
	}

	private long maletaItem(long maleta, long produto, int quantidade, String custo) {
		return id("INSERT INTO maleta_itens (maleta_id, produto_id, quantidade, preco_custo) VALUES (?, ?, ?, ?) RETURNING id",
				maleta, produto, quantidade, new BigDecimal(custo));
	}

	/** Cria a peça (o banco gera o número da etiqueta) e lança a ENTRADA dela. */
	private Peca peca(long produto, long maletaItem) {
		Peca peca = new Peca(
				id("INSERT INTO pecas (produto_id, maleta_item_id) VALUES (?, ?) RETURNING id", produto, maletaItem),
				produto, maletaItem);
		movimentar(peca, null, "ENTRADA");
		return peca;
	}

	private void movimentar(Peca peca, Long venda, String tipo) {
		int quantidade = switch (tipo) {
			case "VENDA", "PERDA", "DEVOLUCAO_FORNECEDOR" -> -1;
			default -> 1;
		};
		int anterior = quantidade < 0 ? 1 : 0;
		jdbc.update("INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, venda_id, usuario_id, "
				+ "tipo, quantidade, estoque_anterior, estoque_posterior) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				peca.produtoId(), peca.maletaItemId(), peca.id(), venda, usuario,
				tipo, quantidade, anterior, anterior + quantidade);
	}

	private long cliente(String nome, String telefone) {
		return id("INSERT INTO clientes (nome, telefone) VALUES (?, ?) RETURNING id", nome, telefone);
	}

	private long venda(long cliente, LocalDate data, String produtos, String desconto, String acrescimo, String status) {
		return id("INSERT INTO vendas (cliente_id, usuario_id, data_venda, valor_produtos, desconto, acrescimo, status, "
				+ "finalizada_em, cancelada_em) VALUES (?, ?, ?, ?, ?, ?, ?, now(), "
				+ "CASE WHEN CAST(? AS text) = 'CANCELADA' THEN now() END) RETURNING id",
				cliente, usuario, data, new BigDecimal(produtos), new BigDecimal(desconto), new BigDecimal(acrescimo),
				status, status);
	}

	/** Item da venda + baixa da peça no estoque (VENDA). */
	private void vender(long venda, Peca peca, String preco, String custo) {
		jdbc.update("INSERT INTO itens_venda (venda_id, produto_id, maleta_item_id, peca_id, preco_unitario, "
				+ "preco_custo_unitario) VALUES (?, ?, ?, ?, ?, ?)",
				venda, peca.produtoId(), peca.maletaItemId(), peca.id(), new BigDecimal(preco), new BigDecimal(custo));
		movimentar(peca, venda, "VENDA");
	}

	private long parcela(long venda, int numero, String valor, LocalDate vencimento, String status) {
		return id("INSERT INTO parcelas (venda_id, numero_parcela, valor, data_vencimento, status) "
				+ "VALUES (?, ?, ?, ?, ?) RETURNING id", venda, numero, new BigDecimal(valor), vencimento, status);
	}

	private long pagar(long parcela, String valor) {
		return id("INSERT INTO pagamentos (parcela_id, usuario_id, valor, data_pagamento, forma_pagamento) "
				+ "VALUES (?, ?, ?, DATE '2026-11-01', 'PIX') RETURNING id", parcela, usuario, new BigDecimal(valor));
	}

	private void estornar(long pagamento) {
		jdbc.update("UPDATE pagamentos SET estornado = TRUE, estornado_em = now(), estornado_por = ?, "
				+ "motivo_estorno = 'lançado em duplicidade' WHERE id = ?", usuario, pagamento);
	}

	private long id(String sql, Object... args) {
		Long id = jdbc.queryForObject(sql, Long.class, args);
		return id;
	}

}
