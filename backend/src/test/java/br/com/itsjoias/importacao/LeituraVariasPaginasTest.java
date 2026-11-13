package br.com.itsjoias.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.itsjoias.importacao.DocumentoLido.Linha;

/**
 * PDF digital de várias páginas no modelo "relação de mercadorias" (dados FICTÍCIOS):
 * coluna Nº antes do código, título da tabela repetido no topo de cada página (com "Desconto  Total"),
 * rodapé "Página N" e resumo do pedido no fim.
 * Antes da correção, o título repetido na página 2 era confundido com o rodapé de totais e a leitura parava na página 1.
 */
class LeituraVariasPaginasTest {

	private static final String TITULO = "   Nº   Código de barras        Descrição                          Qtd.   Valor unit.   Desconto     Total";
	private static final String[] PECAS = { "BRINCO OURO ARGOLA LISA", "ANEL PRATA SOLITARIO CRISTAL", "COLAR OURO ELO PORTUGUES",
			"PULSEIRA PRATA ELO OVAL", "BRINCO PRATA FLOR MIOLO ZIRCONIA", "COLAR PRATA MINI CARTIER" };

	private static final InterpretadorDocumento INTERPRETADOR =
			new InterpretadorDocumento(List.of("BRINCO", "ANEL", "COLAR", "PULSEIRA", "MOSTRUARIO"));

	/** 3 páginas com 4 peças em cada uma (12 peças, R$ 10,00 a R$ 120,00). */
	private static List<String> documento() {
		List<String> linhas = new ArrayList<>();
		linhas.add("                MALETA DE TESTE — RELAÇÃO DE MERCADORIAS (FICTÍCIO)");
		linhas.add("   Pedido nº 99777 • Data de criação: 06/11/2026 • Acerto: 07/12/2026 17:57 • Tipo de preço: Atacado");
		int n = 0;
		for (int pagina = 1; pagina <= 3; pagina++) {
			if (pagina > 1) {
				linhas.add("\f");
			}
			linhas.add(TITULO);
			for (int i = 0; i < 4; i++) {
				n++;
				String codigo = ImportacaoServiceTest.ean8(String.format("%07d", 1000000 + n * 1111));
				String valor = String.format("R$ %d,00", n * 10);
				linhas.add(String.format("  %2d    %s     %-40s  1   %s   R$ 0,00   %s", n, codigo, PECAS[n % PECAS.length],
						valor, valor));
			}
			linhas.add("Transcrição organizada a partir do documento enviado                      Página " + pagina);
		}
		linhas.add("   RESUMO DO PEDIDO                         QUANTIDADE     VALOR");
		linhas.add("   Quantidade total de itens                    12          R$ 780,00");
		linhas.add("   Desconto total                                —          R$ 0,00");
		linhas.add("   Crédito                                       —          R$ 0,00");
		linhas.add("   Valor total                                   —          R$ 780,00");
		return linhas;
	}

	@Test
	void leTodasAsPaginas() {
		DocumentoLido lido = INTERPRETADOR.interpretar(documento());

		assertThat(lido.linhas()).hasSize(12);
		assertThat(lido.linhas()).allSatisfy(l -> {
			assertThat(CodigoBarras.conferir(l.codigo())).isEqualTo(CodigoBarras.Resultado.VALIDO);
			assertThat(l.quantidade()).isEqualTo(1);
			assertThat(l.valoresBatem()).isTrue();
		});
		assertThat(lido.linhas().get(11).valorUnitario()).isEqualByComparingTo("120.00");
		assertThat(lido.linhas().stream().map(Linha::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
				.isEqualByComparingTo("780.00");
	}

	@Test
	void rodapeDaPaginaNaoGrudaNaDescricao() {
		DocumentoLido lido = INTERPRETADOR.interpretar(documento());

		assertThat(lido.linhas()).extracting(Linha::descricao)
				.noneMatch(d -> d.toUpperCase().contains("TRANSCRI") || d.toUpperCase().contains("PAGINA"))
				.noneMatch(d -> d.toUpperCase().contains("RESUMO") || d.toUpperCase().contains("CREDITO"));
	}

	@Test
	void cabecalhoComAcertoEResumo() {
		DocumentoLido lido = INTERPRETADOR.interpretar(documento());

		assertThat(lido.cabecalho().pedido()).isEqualTo("99777");
		assertThat(lido.cabecalho().dataCriacao()).isEqualTo(LocalDate.of(2026, 11, 6));
		assertThat(lido.cabecalho().dataAcerto()).isEqualTo(LocalDate.of(2026, 12, 7));
		assertThat(lido.cabecalho().valorTotalItens()).isEqualByComparingTo("780.00");
	}

	@Test
	void pdfDigitalDeVariasPaginasPeloLeitor() {
		byte[] pdf = PdfDeTeste.comLinhas(documento());
		LeitorPdf.Leitura leitura = new LeitorPdf("tesseract", "", 60).ler(pdf);

		assertThat(leitura.metodo()).isEqualTo(LeitorPdf.Metodo.TEXTO);
		assertThat(leitura.paginas()).isEqualTo(3);
		assertThat(INTERPRETADOR.interpretar(leitura.principal()).linhas()).hasSize(12);
	}

}
