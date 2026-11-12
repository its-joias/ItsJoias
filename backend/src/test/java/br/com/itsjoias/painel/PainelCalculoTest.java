package br.com.itsjoias.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.dto.PainelDTO.MaletaAtiva;
import br.com.itsjoias.dto.PainelDTO.ParcelaAVencer;
import br.com.itsjoias.dto.PainelDTO.ParcelaAtrasada;
import br.com.itsjoias.dto.PainelDTO.SituacaoParcela;
import br.com.itsjoias.dto.PainelDTO.Totais;
import br.com.itsjoias.repository.PainelRepository;
import br.com.itsjoias.service.CalendarioCobranca;
import br.com.itsjoias.service.PainelService;

/**
 * Cálculo do Painel contra o Postgres de verdade (as contas são SQL sobre as views e
 * respeitam as triggers do banco). Precisa de um banco de TESTE vazio, criado com
 * database/its_joias_banco.sql + database/alteracoes (ver database/README.md).
 * Cada teste monta o cenário e tudo é desfeito (rollback) no final.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("teste")
@Import(PainelRepository.class)
class PainelCalculoTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PainelRepository painelRepository;

	@BeforeEach
	void bancoDeTesteVazio() {
		Integer vendas = jdbc.queryForObject("SELECT COUNT(*) FROM vendas", Integer.class);
		Integer maletas = jdbc.queryForObject("SELECT COUNT(*) FROM maletas", Integer.class);
		assertThat(vendas + maletas)
				.as("O banco de teste precisa estar vazio (use um banco só para teste, nunca o de produção)")
				.isZero();
	}

	/** Painel "visto" num instante qualquer (em UTC, como num servidor). */
	private PainelDTO painelEm(String instanteUtc) {
		Clock relogio = Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC);
		return new PainelService(painelRepository, new CalendarioCobranca(relogio)).montarPainel();
	}

	private static BigDecimal reais(String valor) {
		return new BigDecimal(valor);
	}

	// ------------------------------------------------------------------------

	@Test
	void bancoNovoSemDadosDevolveTudoZerado() {
		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");
		Totais t = painel.totais();

		assertThat(t.totalVendido().valor()).isEqualTo(reais("0.00"));
		assertThat(t.totalVendido().quantidadeVendas()).isZero();
		assertThat(t.recebido().valor()).isEqualTo(reais("0.00"));
		assertThat(t.recebido().percentualDoVendido()).isNull();
		assertThat(t.aReceber().valor()).isEqualTo(reais("0.00"));
		assertThat(t.atrasado().valor()).isEqualTo(reais("0.00"));
		assertThat(t.clientesDevendo().quantidade()).isZero();
		assertThat(t.proximoDia5().data()).isEqualTo(LocalDate.of(2026, 12, 5));
		assertThat(t.proximoDia5().valor()).isEqualTo(reais("0.00"));
		assertThat(painel.parcelasAtrasadas()).isEmpty();
		assertThat(painel.vencemNoProximoDia5()).isEmpty();
		assertThat(painel.maletasAtivas()).isEmpty();
	}

	@Test
	void totaisIgnoramVendaCanceladaParcelaCanceladaEPagamentoEstornado() {
		new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");
		Totais t = painel.totais();

		assertThat(painel.hoje()).isEqualTo(LocalDate.of(2026, 11, 11));
		// V3 (cancelada, R$ 100) fora do vendido
		assertThat(t.totalVendido().valor()).isEqualTo(reais("355.00"));
		assertThat(t.totalVendido().quantidadeVendas()).isEqualTo(3);
		// 55 + 20; o pagamento estornado de 30 não conta
		assertThat(t.recebido().valor()).isEqualTo(reais("75.00"));
		assertThat(t.recebido().percentualDoVendido()).isEqualTo(reais("21.1"));
		assertThat(t.aReceber().valor()).isEqualTo(reais("280.00"));
		assertThat(t.aReceber().quantidadeParcelas()).isEqualTo(6);
		// parcial da Ana (35) + 1ª da Bia (30); a parcela cancelada da Bia e a da V3 ficam fora
		assertThat(t.atrasado().valor()).isEqualTo(reais("65.00"));
		assertThat(t.atrasado().quantidadeParcelas()).isEqualTo(2);
		assertThat(t.clientesDevendo().quantidade()).isEqualTo(3);
		assertThat(t.clientesDevendo().comParcelaAtrasada()).isEqualTo(2);
		assertThat(t.proximoDia5().data()).isEqualTo(LocalDate.of(2026, 12, 5));
		assertThat(t.proximoDia5().valor()).isEqualTo(reais("185.00"));
		assertThat(t.proximoDia5().quantidadeParcelas()).isEqualTo(3);
	}

	@Test
	void parcelasAtrasadasDaMaisParaAMenosAtrasadaComParcialEDiasDeAtraso() {
		CenarioPainel c = new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");

		assertThat(painel.parcelasAtrasadas())
				.extracting(ParcelaAtrasada::clienteNome, ParcelaAtrasada::vendaId,
						ParcelaAtrasada::numeroParcela, ParcelaAtrasada::totalParcelas,
						ParcelaAtrasada::dataVencimento, ParcelaAtrasada::valorPago, ParcelaAtrasada::saldo,
						ParcelaAtrasada::situacao, ParcelaAtrasada::diasAtraso)
				.containsExactly(
						tuple("Bia Teste", c.vendaBia, 1, 3, LocalDate.of(2026, 10, 5),
								reais("0.00"), reais("30.00"), SituacaoParcela.PENDENTE, 37),
						tuple("Ana Teste", c.vendaAna, 2, 3, LocalDate.of(2026, 11, 5),
								reais("20.00"), reais("35.00"), SituacaoParcela.PARCIAL, 6));
		// rodapé do bloco = card "atrasado"
		assertThat(painel.totais().atrasado().valor()).isEqualTo(reais("65.00"));
	}

	@Test
	void parcelaQueVenceHojeNaoEstaAtrasada() {
		CenarioPainel c = new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");

		// 2ª parcela da Bia vence em 11/11 = hoje
		assertThat(painel.parcelasAtrasadas())
				.noneMatch(p -> p.vendaId() == c.vendaBia && p.numeroParcela() == 2);
	}

	@Test
	void vencemNoProximoDia5() {
		CenarioPainel c = new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");

		assertThat(painel.vencemNoProximoDia5())
				.extracting(ParcelaAVencer::clienteNome, ParcelaAVencer::vendaId, ParcelaAVencer::numeroParcela,
						ParcelaAVencer::totalParcelas, ParcelaAVencer::saldo)
				.containsExactly(
						tuple("Ana Teste", c.vendaAna, 3, 3, reais("55.00")), // estorno não abate o saldo
						tuple("Bia Teste", c.vendaBia, 3, 3, reais("30.00")),
						tuple("Eva Teste", c.vendaEva, 1, 1, reais("100.00")));
	}

	@Test
	void noDia5AsParcelasDeHojeAindaSaoDoProximoDia5ENaoEstaoAtrasadas() {
		new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-12-05T15:00:00Z");
		Totais t = painel.totais();

		assertThat(t.proximoDia5().data()).isEqualTo(LocalDate.of(2026, 12, 5));
		assertThat(t.proximoDia5().valor()).isEqualTo(reais("185.00"));
		assertThat(t.proximoDia5().quantidadeParcelas()).isEqualTo(3);
		assertThat(painel.parcelasAtrasadas())
				.noneMatch(p -> p.dataVencimento().equals(LocalDate.of(2026, 12, 5)));
		// Ana #2 (35) + Bia #1 (30) + Bia #2 (30, vencida em 11/11)
		assertThat(t.atrasado().valor()).isEqualTo(reais("95.00"));
		assertThat(t.atrasado().quantidadeParcelas()).isEqualTo(3);
	}

	@Test
	void noDia6OProximoDia5ViraOMesEAsDoDia5FicamAtrasadas() {
		new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-12-06T15:00:00Z");
		Totais t = painel.totais();

		assertThat(t.proximoDia5().data()).isEqualTo(LocalDate.of(2027, 1, 5));
		assertThat(t.proximoDia5().valor()).isEqualTo(reais("0.00"));
		assertThat(t.proximoDia5().quantidadeParcelas()).isZero();
		assertThat(painel.vencemNoProximoDia5()).isEmpty();
		// tudo que está em aberto já venceu
		assertThat(t.atrasado().valor()).isEqualTo(reais("280.00"));
		assertThat(t.atrasado().quantidadeParcelas()).isEqualTo(6);
		assertThat(t.clientesDevendo().comParcelaAtrasada()).isEqualTo(3);
		assertThat(painel.parcelasAtrasadas())
				.filteredOn(p -> p.dataVencimento().equals(LocalDate.of(2026, 12, 5)))
				.extracting(ParcelaAtrasada::diasAtraso)
				.containsOnly(1);
	}

	@Test
	void fusoDeSaoPauloPertoDaMeiaNoite() {
		CenarioPainel c = new CenarioPainel(jdbc);

		// 06/11 02:30 UTC = 05/11 23:30 em São Paulo: a parcela #2 da Ana (vence 05/11) vence HOJE
		PainelDTO antes = painelEm("2026-11-06T02:30:00Z");
		assertThat(antes.hoje()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(antes.parcelasAtrasadas())
				.noneMatch(p -> p.vendaId() == c.vendaAna);
		assertThat(antes.totais().proximoDia5().data()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(antes.vencemNoProximoDia5())
				.extracting(ParcelaAVencer::vendaId, ParcelaAVencer::numeroParcela, ParcelaAVencer::saldo)
				.containsExactly(tuple(c.vendaAna, 2, reais("35.00")));

		// 06/11 03:30 UTC = 06/11 00:30 em São Paulo: agora está 1 dia atrasada
		PainelDTO depois = painelEm("2026-11-06T03:30:00Z");
		assertThat(depois.hoje()).isEqualTo(LocalDate.of(2026, 11, 6));
		assertThat(depois.parcelasAtrasadas())
				.filteredOn(p -> p.vendaId() == c.vendaAna)
				.extracting(ParcelaAtrasada::numeroParcela, ParcelaAtrasada::diasAtraso, ParcelaAtrasada::situacao)
				.containsExactly(tuple(2, 1, SituacaoParcela.PARCIAL));
		assertThat(depois.totais().proximoDia5().data()).isEqualTo(LocalDate.of(2026, 12, 5));
	}

	@Test
	void maletasAtivasContamPecasPelaUltimaMovimentacao() {
		CenarioPainel c = new CenarioPainel(jdbc);

		PainelDTO painel = painelEm("2026-11-11T15:00:00Z");

		// a Maleta Antiga está ENCERRADA e não aparece
		assertThat(painel.maletasAtivas()).hasSize(1);
		MaletaAtiva m = painel.maletasAtivas().get(0);
		assertThat(m.maletaId()).isEqualTo(c.maletaOutubro);
		assertThat(m.nome()).isEqualTo("Maleta Outubro");
		assertThat(m.fornecedor()).isEqualTo("Fornecedora Teste");
		assertThat(m.dataEntrada()).isEqualTo(CenarioPainel.ENTRADA_MALETA);
		assertThat(m.pecasRecebidas()).isEqualTo(7);
		assertThat(m.pecasVendidas()).isEqualTo(4);
		// P1-03 voltou para a maleta com o cancelamento da V3
		assertThat(m.pecasDisponiveis()).isEqualTo(1);
		assertThat(m.pecasPerdidas()).isEqualTo(1);
		assertThat(m.pecasDevolvidas()).isEqualTo(1);
		assertThat(m.pecasPerdidasOuDevolvidas()).isEqualTo(2);
		assertThat(m.custoTotal()).isEqualTo(reais("220.00"));
		// itens das vendas finalizadas (a V3 cancelada não entra)
		assertThat(m.totalVendido()).isEqualTo(reais("350.00"));
	}

}
