package br.com.itsjoias.importacao;

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
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.itsjoias.dto.ImportacaoDTO.DadosMaleta;
import br.com.itsjoias.dto.ImportacaoDTO.DecisaoPreco;
import br.com.itsjoias.dto.ImportacaoDTO.LinhaConferida;
import br.com.itsjoias.dto.ImportacaoDTO.LinhaImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.NivelAviso;
import br.com.itsjoias.dto.ImportacaoDTO.PedidoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.Resultado;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.repository.ImportacaoRepository;
import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.UsuarioAtual;
import br.com.itsjoias.seguranca.UsuarioLogado;
import br.com.itsjoias.service.CalendarioCobranca;
import br.com.itsjoias.service.ImportacaoService;

/**
 * Importação de maleta contra o Postgres de teste (vazio; tudo desfeito no fim) e os programas
 * de PDF/OCR instalados na máquina (poppler-utils, tesseract-ocr).
 * Todos os dados abaixo são FICTÍCIOS, montados no layout do "Termo de Entrega" da It Store.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("teste")
@Import({ ImportacaoRepository.class, LeitorPdf.class })
class ImportacaoServiceTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ImportacaoRepository repo;

	@Autowired
	private LeitorPdf leitor;

	private ImportacaoService service;
	private long brinco;
	private long anel;
	private long colar;
	private long pulseira;

	// códigos EAN-8 válidos (o último dígito é o verificador)
	static final String C_MOSTRUARIO = ean8("1000001");
	static final String C_ARGOLA = ean8("2000002");
	static final String C_ANEL = ean8("3000003");
	static final String C_COLAR = ean8("4000004");
	static final String C_PULSEIRA = ean8("5000005");
	/** Código da pulseira com um dígito trocado (como o OCR erra): NÃO passa no verificador. */
	static final String C_PULSEIRA_ERRADO = C_PULSEIRA.substring(0, 3) + (C_PULSEIRA.charAt(3) == '9' ? '1' : '9')
			+ C_PULSEIRA.substring(4);

	@BeforeEach
	void preparar() {
		Integer existentes = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM maletas) + (SELECT COUNT(*) FROM fornecedores)", Integer.class);
		assertThat(existentes).as("O banco de teste precisa estar vazio").isZero();
		long usuario = jdbc.queryForObject("INSERT INTO usuarios (nome, email, perfil) VALUES ('Leide Teste', 'leide@example.com', 'PROPRIETARIA') RETURNING id", Long.class);
		UsuarioAtual atual = mock(UsuarioAtual.class);
		when(atual.obter()).thenReturn(new UsuarioLogado(usuario, "Leide Teste", "leide@example.com", Perfil.PROPRIETARIA));
		CalendarioCobranca calendario = new CalendarioCobranca(Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneOffset.UTC));
		service = new ImportacaoService(leitor, repo, calendario, atual);
		brinco = categoria("B");
		anel = categoria("A");
		colar = categoria("C");
		pulseira = categoria("P");
	}

	private long categoria(String prefixo) {
		return jdbc.queryForObject("SELECT id FROM categorias WHERE prefixo = ?", Long.class, prefixo);
	}

	static String ean8(String sete) {
		int soma = 0;
		for (int i = 0; i < 7; i++) {
			soma += (sete.charAt(i) - '0') * (i % 2 == 0 ? 3 : 1);
		}
		return sete + (10 - soma % 10) % 10;
	}

	/** Termo fictício: mostruário, descrição em duas linhas, código errado e código repetido. */
	static byte[] termoFicticio(String pedido, String precoArgola) {
		return PdfDeTeste.comLinhas(List.of(
				"TERMO DE ENTREGA DE MERCADORIA - DETALHADO   (EXEMPLO FICTICIO PARA TESTE)",
				"Fornecedora Teste Semijoias Ltda.                         CNPJ: 11.222.333/0001-81",
				"Pedido Nº " + pedido + "      Data Criação: 01/11/2026      Data Acerto: 01/12/2026",
				"Cód. Barras  Descrição                                 Qtde  Valor Unit.  Desconto  V. Total  TP",
				C_MOSTRUARIO + "  MOS0001 - MALETA RÍGIDA PEQUENA                 1   R$ 150,00   R$ 0,00   R$ 150,00",
				C_ARGOLA + "  BRINCO OURO ARGOLA LISA MÉDIA                    2   R$ " + precoArgola + "    R$ 0,00   R$ 90,00",
				"          ANEL PRATA SOLITÁRIO ZIRCÔNIA",
				C_ANEL + "  CRAVEJADO                                       1   R$ 39,90    R$ 0,00   R$ 39,90",
				C_COLAR + "  COLAR OURO GARGANTILHA VENEZIANA FINA           1   R$ 79,00    R$ 0,00   R$ 79,00",
				C_PULSEIRA_ERRADO + "  PULSEIRA PRATA ELO PORTUGUÊS                1   R$ 55,00    R$ 0,00   R$ 55,00",
				C_ARGOLA + "  BRINCO OURO ARGOLA LISA MÉDIA                    1   R$ " + precoArgola + "    R$ 0,00   R$ 45,00",
				"Desconto Total Itens: R$ 0,00                Valor Total Itens: R$ 458,90",
				"Categoria / Subcategoria          Qtde      Valor Total",
				"BRINCO                               3      R$ 135,00"));
	}

	/** Aplica correções às linhas da prévia, como a vendedora faria na tela. */
	private PedidoImportacao corrigir(Previa p, UnaryOperator<LinhaImportacao> correcao, boolean reimportar) {
		List<LinhaImportacao> linhas = new ArrayList<>();
		for (LinhaConferida c : p.linhas()) {
			linhas.add(correcao.apply(c.linha()));
		}
		return new PedidoImportacao(p.arquivo(), p.documento(), p.maleta(), linhas, reimportar);
	}

	private static LinhaImportacao com(LinhaImportacao l, Boolean importar, String codigo, Integer qtd, DecisaoPreco decisao) {
		return new LinhaImportacao(l.indice(), importar != null ? importar : l.importar(), codigo != null ? codigo : l.codigo(),
				l.descricao(), l.categoriaId(), qtd != null ? qtd : l.quantidade(), l.valorUnitario(), l.codigoConferido(),
				decisao != null ? decisao : l.decisaoPreco(), l.textoOriginal(), l.valorTotalLido(), l.quantidadeDeduzida());
	}

	/** Correções da 1ª importação: soma as argolas na linha 2, desmarca a repetida, conserta o código da pulseira. */
	private LinhaImportacao correcoesPadrao(LinhaImportacao l) {
		return switch (l.indice()) {
			case 2 -> com(l, null, null, 3, null);
			case 5 -> com(l, null, C_PULSEIRA, null, null);
			case 6 -> com(l, false, null, null, null);
			default -> l;
		};
	}

	private static List<String> mensagens(LinhaConferida c, NivelAviso nivel) {
		return c.avisos().stream().filter(a -> a.nivel() == nivel).map(a -> a.mensagem()).toList();
	}

	// ------------------------------------------------------------------------------------------

	@Test
	void pdfDigitalViraPreviaComDocumentoECategorias() {
		Previa p = service.ler("termo-99001.pdf", termoFicticio("99001", "45,00"));

		assertThat(p.arquivo().metodo()).isEqualTo("TEXTO");
		assertThat(p.arquivo().hash()).hasSize(64);
		assertThat(p.documento().pedido()).isEqualTo("99001");
		assertThat(p.documento().cnpj()).isEqualTo("11.222.333/0001-81");
		assertThat(p.documento().dataAcerto()).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(p.documento().valorTotalDocumento()).isEqualByComparingTo("458.90");
		assertThat(p.maleta().codigo()).isEqualTo("PED99001");
		assertThat(p.maleta().nome()).isEqualTo("Maleta acerto 01/12/2026");
		assertThat(p.maleta().fornecedorNovoNome()).isEqualTo("Fornecedora Teste Semijoias Ltda.");

		assertThat(p.linhas()).extracting(c -> c.linha().codigo(), c -> c.linha().descricao(), c -> c.linha().quantidade(),
				c -> c.linha().categoriaId(), c -> c.linha().importar()).containsExactly(
						tuple(C_MOSTRUARIO, "MOS0001 - MALETA RÍGIDA PEQUENA", 1, null, false),
						tuple(C_ARGOLA, "BRINCO OURO ARGOLA LISA MÉDIA", 2, brinco, true),
						tuple(C_ANEL, "ANEL PRATA SOLITÁRIO ZIRCÔNIA CRAVEJADO", 1, anel, true),
						tuple(C_COLAR, "COLAR OURO GARGANTILHA VENEZIANA FINA", 1, colar, true),
						tuple(C_PULSEIRA_ERRADO, "PULSEIRA PRATA ELO PORTUGUÊS", 1, pulseira, true),
						tuple(C_ARGOLA, "BRINCO OURO ARGOLA LISA MÉDIA", 1, brinco, true));
		// a soma das linhas bate com o total do documento: nenhum aviso de linha faltando
		assertThat(p.avisosGerais()).noneMatch(a -> a.mensagem().contains("não bate"));
	}

	@Test
	void duplicidadeECodigoErradoImpedemAConfirmacao() {
		Previa p = service.ler("termo.pdf", termoFicticio("99001", "45,00"));

		assertThat(p.podeConfirmar()).isFalse();
		assertThat(mensagens(p.linhas().get(1), NivelAviso.ERRO)).anyMatch(m -> m.contains("Código repetido no arquivo (linhas 2, 6)"));
		assertThat(mensagens(p.linhas().get(4), NivelAviso.ERRO)).anyMatch(m -> m.contains("dígito verificador"));
		assertThat(mensagens(p.linhas().get(0), NivelAviso.INFO)).anyMatch(m -> m.contains("Mostruário"));
		assertThatThrownBy(() -> service.confirmar(corrigir(p, l -> l, false)))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("linha(s) com erro");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM maletas", Integer.class)).as("nada foi gravado").isZero();
	}

	@Test
	void codigoConferidoPelaVendedoraLiberaALinha() {
		Previa p = service.ler("termo.pdf", termoFicticio("99001", "45,00"));
		Previa v = service.validar(corrigir(p, l -> l.indice() == 5
				? new LinhaImportacao(5, true, l.codigo(), l.descricao(), l.categoriaId(), 1, l.valorUnitario(), true, null,
						l.textoOriginal(), l.valorTotalLido(), false)
				: l, false));

		assertThat(v.linhas().get(4).temErro()).isFalse();
	}

	@Test
	void confirmarGravaMaletaModelosPecasEtiquetasEHistorico() {
		Previa p = service.ler("termo-99001.pdf", termoFicticio("99001", "45,00"));
		Previa corrigida = service.validar(corrigir(p, this::correcoesPadrao, false));
		assertThat(corrigida.podeConfirmar()).isTrue();
		assertThat(corrigida.resumo().pecas()).isEqualTo(6);
		assertThat(corrigida.resumo().valorTotal()).isEqualByComparingTo("308.90"); // 3×45 + 39,90 + 79 + 55

		Resultado r = service.confirmar(corrigir(p, this::correcoesPadrao, false));
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"); // conferências que o banco faz no COMMIT

		assertThat(r.pecas()).isEqualTo(6);
		assertThat(r.modelosNovos()).isEqualTo(4);
		assertThat(r.linhasIgnoradas()).isEqualTo(2);
		assertThat(r.etiquetas()).extracting(e -> e.etiqueta())
				.containsExactly("A0001-01", "B0001-01", "B0001-02", "B0001-03", "C0001-01", "P0001-01");
		assertThat(jdbc.queryForMap("SELECT codigo, nome, data_entrada, data_acerto, status FROM maletas WHERE id = ?", r.maletaId()))
				.containsEntry("codigo", "PED99001").containsEntry("status", "ATIVA");
		assertThat(jdbc.queryForObject("SELECT cnpj FROM fornecedores", String.class)).isEqualTo("11.222.333/0001-81");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimentacoes_estoque WHERE tipo = 'ENTRADA'", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForList("SELECT produto_id FROM vw_divergencia_estoque")).as("estoque bate com o histórico").isEmpty();
		assertThat(jdbc.queryForMap("SELECT arquivo_nome, metodo_leitura, pecas_importadas, modelos_novos, linhas_ignoradas, valor_total FROM importacoes_maleta"))
				.containsEntry("arquivo_nome", "termo-99001.pdf").containsEntry("metodo_leitura", "TEXTO")
				.containsEntry("pecas_importadas", 6).containsEntry("modelos_novos", 4).containsEntry("linhas_ignoradas", 2);
		// as peças já aparecem disponíveis para venda
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vw_pecas WHERE situacao = 'DISPONIVEL'", Integer.class)).isEqualTo(6);
	}

	@Test
	void segundaMaletaReconheceModeloJaCadastradoEPedeDecisaoDePreco() {
		service.confirmar(corrigir(service.ler("termo-99001.pdf", termoFicticio("99001", "45,00")), this::correcoesPadrao, false));

		// nova remessa do mesmo fornecedor, com a argola mais cara
		Previa p = service.ler("termo-99002.pdf", termoFicticio("99002", "49,00"));
		assertThat(p.maleta().fornecedorId()).as("fornecedor reconhecido pelo CNPJ").isNotNull();
		LinhaConferida argola = p.linhas().get(1);
		assertThat(argola.produtoExistente()).isNotNull();
		assertThat(argola.produtoExistente().codigo()).isEqualTo("B0001");
		assertThat(mensagens(argola, NivelAviso.ERRO)).anyMatch(m -> m.contains("é diferente do cadastrado (R$ 45,00)"));

		UnaryOperator<LinhaImportacao> decide = l -> l.indice() == 2 ? com(correcoesPadrao(l), null, null, null, DecisaoPreco.ATUALIZAR)
				: correcoesPadrao(l);
		Resultado r = service.confirmar(corrigir(p, decide, false));
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

		assertThat(r.modelosNovos()).isZero();
		assertThat(jdbc.queryForObject("SELECT preco_venda FROM produtos WHERE codigo = 'B0001'", BigDecimal.class))
				.isEqualByComparingTo("49.00");
		// a numeração das etiquetas continua de onde parou
		assertThat(r.etiquetas()).extracting(e -> e.etiqueta()).contains("B0001-04", "B0001-05", "B0001-06");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM produtos", Integer.class)).isEqualTo(4);
	}

	@Test
	void mesmoArquivoDeNovoExigeConfirmacao() {
		byte[] pdf = termoFicticio("99001", "45,00");
		service.confirmar(corrigir(service.ler("termo.pdf", pdf), this::correcoesPadrao, false));

		Previa de_novo = service.ler("termo (1).pdf", pdf);
		assertThat(de_novo.importacoesDoMesmoArquivo()).hasSize(1);
		assertThat(de_novo.avisosGerais()).anyMatch(a -> a.mensagem().contains("já foi importado"));
		assertThat(de_novo.avisosMaleta()).anyMatch(a -> a.mensagem().contains("Já existe uma maleta com o código PED99001"));

		// mesmo trocando o código da maleta, sem a confirmação de reimportação não grava
		DadosMaleta m = de_novo.maleta();
		DadosMaleta outra = new DadosMaleta(m.fornecedorId(), null, null, "PED99001-B", m.nome(), m.dataEntrada(), m.dataAcerto());
		PedidoImportacao sem = new PedidoImportacao(de_novo.arquivo(), de_novo.documento(), outra,
				corrigir(de_novo, l -> com(correcoesPadrao(l), null, null, null, DecisaoPreco.MANTER), false).linhas(), false);
		assertThatThrownBy(() -> service.confirmar(sem)).hasMessageContaining("já foi importado antes");

		PedidoImportacao com = new PedidoImportacao(sem.arquivo(), sem.documento(), outra, sem.linhas(), true);
		service.confirmar(com);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM importacoes_maleta WHERE reimportacao_confirmada", Integer.class)).isEqualTo(1);
	}

	@Test
	void arquivoQueNaoEPdfOuEstaCorrompido() {
		assertThatThrownBy(() -> service.ler("foto.jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2, 3, 4 }))
				.hasMessageContaining("não é um PDF");
		assertThatThrownBy(() -> service.ler("corrompido.pdf", "%PDF-1.4\nisto não é um pdf de verdade".getBytes()))
				.hasMessageContaining("corrompido");
		assertThatThrownBy(() -> service.ler("vazio.pdf", new byte[0])).hasMessageContaining("vazio");
	}

	@Test
	void pdfSemNenhumaPecaReconhecida() {
		byte[] pdf = PdfDeTeste.comLinhas(List.of("Relatório mensal de vendas da loja (texto sem tabela de peças)",
				"Este documento não tem nenhuma linha de mercadoria com código e valor."));
		assertThatThrownBy(() -> service.ler("relatorio.pdf", pdf)).hasMessageContaining("Não foi encontrada nenhuma peça");
	}

}
