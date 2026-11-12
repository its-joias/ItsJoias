package br.com.itsjoias.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.dto.VendaDTO.FormaPagamento;
import br.com.itsjoias.dto.VendaDTO.FormaVenda;
import br.com.itsjoias.dto.VendaDTO.ItemPrevia;
import br.com.itsjoias.dto.VendaDTO.ParcelaPrevia;
import br.com.itsjoias.dto.VendaDTO.Previa;
import br.com.itsjoias.dto.VendaDTO.VendaCriada;
import br.com.itsjoias.dto.VendaDTO.VendaForm;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.repository.PainelRepository;
import br.com.itsjoias.repository.VendaRepository;
import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.UsuarioAtual;
import br.com.itsjoias.seguranca.UsuarioLogado;
import br.com.itsjoias.service.CalendarioCobranca;
import br.com.itsjoias.service.PainelService;
import br.com.itsjoias.service.VendaService;

/**
 * Venda contra o Postgres de verdade (banco de TESTE vazio; tudo é desfeito no final).
 *
 * Cenário: Colar (R$ 100,00) com 2 peças na Maleta Agosto (mais antiga) e 2 na Maleta Outubro;
 * Brinco (R$ 49,90) com 1 peça na Maleta Outubro. Hoje = 11/11/2026 em São Paulo.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("teste")
@Import({ VendaRepository.class, PainelRepository.class })
class VendaServiceTest {

	private static final LocalDate HOJE = LocalDate.of(2026, 11, 11);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private VendaRepository vendaRepository;

	@Autowired
	private PainelRepository painelRepository;

	private VendaService vendas;
	private PainelService painel;
	private long usuario;
	private long ana;
	private long inativa;
	private String colar;   // código do modelo, ex.: C0001
	private String brinco;  // ex.: B0001

	@BeforeEach
	void montarCenario() {
		Integer existentes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM vendas) + (SELECT COUNT(*) FROM maletas)", Integer.class);
		assertThat(existentes).as("O banco de teste precisa estar vazio").isZero();

		usuario = id("INSERT INTO usuarios (nome, email, perfil) VALUES ('Leide Teste', 'leide@example.com', 'PROPRIETARIA') RETURNING id");
		long fornecedor = id("INSERT INTO fornecedores (nome) VALUES ('Fornecedora Teste') RETURNING id");
		long pColar = id("INSERT INTO produtos (nome, categoria_id, preco_venda) SELECT 'Colar veneziana', id, 100.00 FROM categorias WHERE prefixo = 'C' RETURNING id");
		long pBrinco = id("INSERT INTO produtos (nome, categoria_id, preco_venda) SELECT 'Brinco argola', id, 49.90 FROM categorias WHERE prefixo = 'B' RETURNING id");
		colar = jdbc.queryForObject("SELECT codigo FROM produtos WHERE id = ?", String.class, pColar);
		brinco = jdbc.queryForObject("SELECT codigo FROM produtos WHERE id = ?", String.class, pBrinco);

		long agosto = id("INSERT INTO maletas (codigo, nome, fornecedor_id, tipo, data_entrada) VALUES ('T-AGO', 'Maleta Agosto', ?, 'NOVA', '2026-08-01') RETURNING id", fornecedor);
		long outubro = id("INSERT INTO maletas (codigo, nome, fornecedor_id, tipo, data_entrada) VALUES ('T-OUT', 'Maleta Outubro', ?, 'NOVA', '2026-10-01') RETURNING id", fornecedor);
		entrada(pColar, agosto, 2, "40.00");
		entrada(pColar, outubro, 2, "42.00");
		entrada(pBrinco, outubro, 1, "18.00");

		ana = id("INSERT INTO clientes (nome, telefone) VALUES ('Ana Teste', '(71) 90000-0001') RETURNING id");
		inativa = id("INSERT INTO clientes (nome, telefone, ativo) VALUES ('Inativa Teste', '(71) 90000-0009', false) RETURNING id");

		Clock relogio = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
		CalendarioCobranca calendario = new CalendarioCobranca(relogio);
		UsuarioAtual atual = mock(UsuarioAtual.class);
		when(atual.obter()).thenReturn(new UsuarioLogado(usuario, "Leide Teste", "leide@example.com", Perfil.PROPRIETARIA));
		vendas = new VendaService(vendaRepository, calendario, atual);
		painel = new PainelService(painelRepository, calendario);
	}

	/** Entrada de maleta: item, peças com ENTRADA e estoque do produto em dia. */
	private void entrada(long produto, long maleta, int quantidade, String custo) {
		long item = id("INSERT INTO maleta_itens (maleta_id, produto_id, quantidade, preco_custo) VALUES (?, ?, ?, ?) RETURNING id",
				maleta, produto, quantidade, new BigDecimal(custo));
		for (int i = 0; i < quantidade; i++) {
			long peca = id("INSERT INTO pecas (produto_id, maleta_item_id) VALUES (?, ?) RETURNING id", produto, item);
			int estoque = jdbc.queryForObject("SELECT quantidade_estoque FROM produtos WHERE id = ?", Integer.class, produto);
			jdbc.update("INSERT INTO movimentacoes_estoque (produto_id, maleta_item_id, peca_id, usuario_id, tipo, quantidade, "
					+ "estoque_anterior, estoque_posterior) VALUES (?, ?, ?, ?, 'ENTRADA', 1, ?, ?)",
					produto, item, peca, usuario, estoque, estoque + 1);
			jdbc.update("UPDATE produtos SET quantidade_estoque = ? WHERE id = ?", estoque + 1, produto);
		}
	}

	private long id(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private static BigDecimal r(String v) {
		return new BigDecimal(v);
	}

	private VendaForm aVista(long cliente, String... codigos) {
		return new VendaForm(cliente, List.of(codigos), false, FormaVenda.A_VISTA, null, null, FormaPagamento.PIX);
	}

	/** Roda agora as conferências que o banco faria no COMMIT (o teste termina em rollback). */
	private void conferirComoNoCommit() {
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
	}

	// ------------------------------------------------------------------------------------------

	@Test
	void peloModeloVendeAPecaDaMaletaMaisAntiga() {
		Previa p = vendas.previa(aVista(ana, colar.toLowerCase(), colar));

		assertThat(p.itens()).extracting(ItemPrevia::etiqueta, ItemPrevia::maleta)
				.containsExactly(tuple(colar + "-01", "Maleta Agosto"), tuple(colar + "-02", "Maleta Agosto"));
	}

	@Test
	void pelaEtiquetaVendeExatamenteAquelaPeca() {
		Previa p = vendas.previa(aVista(ana, colar + "-4", brinco + "-01"));

		assertThat(p.itens()).extracting(ItemPrevia::etiqueta, ItemPrevia::preco)
				.containsExactly(tuple(colar + "-04", r("100.00")), tuple(brinco + "-01", r("49.90")));
		assertThat(p.valorProdutos()).isEqualTo(r("149.90"));
	}

	@Test
	void etiquetaEModeloJuntosNaoRepetemAPeca() {
		// a etiqueta -01 é reservada primeiro; o modelo pega a próxima da maleta mais antiga (-02)
		Previa p = vendas.previa(aVista(ana, colar, colar + "-01"));

		assertThat(p.itens()).extracting(ItemPrevia::etiqueta).containsExactly(colar + "-02", colar + "-01");
	}

	@Test
	void previaParceladaComEntradaEAcrescimo() {
		VendaForm form = new VendaForm(ana, List.of(colar, brinco), true, FormaVenda.PARCELADO, 3, r("20.00"),
				FormaPagamento.DINHEIRO);

		Previa p = vendas.previa(form);

		assertThat(p.dataVenda()).isEqualTo(HOJE);
		assertThat(p.valorProdutos()).isEqualTo(r("149.90"));
		assertThat(p.acrescimo()).isEqualTo(r("14.99"));
		assertThat(p.valorTotal()).isEqualTo(r("164.89"));
		assertThat(p.pagoNaVenda()).isEqualTo(r("20.00"));
		assertThat(p.saldoAPagar()).isEqualTo(r("144.89"));
		assertThat(p.parcelas()).extracting(ParcelaPrevia::valor, ParcelaPrevia::vencimento, ParcelaPrevia::pagaNaVenda)
				.containsExactly(tuple(r("20.00"), HOJE, true),
						tuple(r("48.29"), LocalDate.of(2026, 12, 5), false),
						tuple(r("48.29"), LocalDate.of(2027, 1, 5), false),
						tuple(r("48.31"), LocalDate.of(2027, 2, 5), false));
		// a prévia não grava nada
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vendas", Integer.class)).isZero();
	}

	@Test
	void confirmarAVistaGravaVendaBaixaEstoqueEPagamento() {
		VendaCriada criada = vendas.confirmar(aVista(ana, colar, brinco));
		conferirComoNoCommit();

		long v = criada.vendaId();
		assertThat(jdbc.queryForMap("SELECT status, valor_produtos, acrescimo, valor_total, data_venda, usuario_id FROM vendas WHERE id = ?", v))
				.containsEntry("status", "FINALIZADA").containsEntry("valor_total", r("149.90"))
				.containsEntry("usuario_id", usuario);
		assertThat(jdbc.queryForList("SELECT situacao FROM vw_pecas vp JOIN itens_venda iv ON iv.peca_id = vp.peca_id WHERE iv.venda_id = ?",
				String.class, v)).containsOnly("VENDIDA").hasSize(2);
		assertThat(jdbc.queryForObject("SELECT SUM(pa.valor) FROM pagamentos pa JOIN parcelas p ON p.id = pa.parcela_id WHERE p.venda_id = ?",
				BigDecimal.class, v)).isEqualTo(r("149.90"));
		// o estoque de cada produto (cache) continua batendo com o histórico
		assertThat(jdbc.queryForList("SELECT produto_id FROM vw_divergencia_estoque")).isEmpty();
	}

	@Test
	void confirmarParceladoDeixaSaldoNoPainelENaCliente() {
		VendaForm form = new VendaForm(ana, List.of(colar + "-03"), false, FormaVenda.PARCELADO, 2, r("10.00"), FormaPagamento.PIX);

		vendas.confirmar(form);
		conferirComoNoCommit();

		PainelDTO p = painel.montarPainel();
		assertThat(p.totais().totalVendido().valor()).isEqualTo(r("100.00"));
		assertThat(p.totais().recebido().valor()).isEqualTo(r("10.00"));
		assertThat(p.totais().aReceber().valor()).isEqualTo(r("90.00"));
		assertThat(p.totais().proximoDia5().data()).isEqualTo(LocalDate.of(2026, 12, 5));
		assertThat(p.totais().proximoDia5().valor()).isEqualTo(r("45.00"));
		assertThat(jdbc.queryForObject("SELECT total_em_aberto FROM vw_saldo_clientes WHERE cliente_id = ?", BigDecimal.class, ana))
				.isEqualTo(r("90.00"));
	}

	@Test
	void pecaJaVendidaNaoPodeSerVendidaDeNovo() {
		vendas.confirmar(aVista(ana, brinco + "-01"));

		assertThatThrownBy(() -> vendas.confirmar(aVista(ana, brinco + "-01")))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("já foi vendida");
		assertThatThrownBy(() -> vendas.previa(aVista(ana, brinco)))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("Não há peça disponível");
	}

	@Test
	void recusasComMensagemClara() {
		assertThatThrownBy(() -> vendas.previa(aVista(ana, colar + "-01", colar + "-1")))
				.hasMessageContaining("mais de uma vez");
		assertThatThrownBy(() -> vendas.previa(aVista(ana, "X9999")))
				.hasMessageContaining("não encontrado");
		assertThatThrownBy(() -> vendas.previa(aVista(ana, "colar bonito")))
				.hasMessageContaining("inválido");
		assertThatThrownBy(() -> vendas.previa(aVista(inativa, colar)))
				.hasMessageContaining("desativada");
		assertThatThrownBy(() -> vendas.previa(new VendaForm(ana, List.of(colar), false, FormaVenda.A_VISTA, null, null, null)))
				.hasMessageContaining("forma de pagamento");
		assertThatThrownBy(() -> vendas.previa(new VendaForm(ana, List.of(colar), false, FormaVenda.PARCELADO, 2, r("100.00"), FormaPagamento.PIX)))
				.hasMessageContaining("menor que o total");
	}

	@Test
	void buscaDePecasDisponiveis() {
		vendas.confirmar(aVista(ana, colar + "-01"));

		assertThat(vendas.buscarPecas(colar, "DISPONIVEL", 30)).extracting(p -> p.etiqueta())
				.containsExactly(colar + "-02", colar + "-03", colar + "-04");
		assertThat(vendas.buscarPecas("argola", "DISPONIVEL", 30)).hasSize(1);
		assertThat(vendas.recentes(null, 30)).singleElement()
				.satisfies(r -> {
					assertThat(r.clienteNome()).isEqualTo("Ana Teste");
					assertThat(r.saldo()).isEqualTo(new BigDecimal("0.00"));
				});
	}

}
