package br.com.itsjoias.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import br.com.itsjoias.importacao.CodigoBarras.Resultado;
import br.com.itsjoias.importacao.DocumentoLido.Linha;

/**
 * Lê um PDF REAL de maleta (escaneado) e mostra o que foi entendido.
 * Só roda quando ITSJOIAS_PDF_REAL aponta para o arquivo (o PDF tem dados pessoais e NÃO vai para o Git):
 *   ITSJOIAS_PDF_REAL=/caminho/Joias_Leide_teste.pdf ./mvnw test -Dtest=LeituraPdfRealTest
 */
@EnabledIfEnvironmentVariable(named = "ITSJOIAS_PDF_REAL", matches = ".+")
class LeituraPdfRealTest {

	/** Códigos de barras da página 1 do termo de teste, conferidos olhando a imagem. */
	private static final Set<String> CODIGOS_PAGINA_1 = Set.of("47035201", "49109894", "46948434", "79012126",
			"41943595", "57564616", "35103080", "59357650", "91784902", "61264960", "77540515", "59152958",
			"70256130", "84384539", "70780796", "39017031", "38519130", "37164379", "60912107", "48351287",
			"14645761", "32862195", "39924803", "79564991", "75296285", "81182619", "49995046", "48370301",
			"31603744", "68868895", "92269446", "24765350", "92393608");

	@Test
	void lerTermoEscaneado() throws Exception {
		System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
		byte[] pdf = Files.readAllBytes(Path.of(System.getenv("ITSJOIAS_PDF_REAL")));
		long inicio = System.currentTimeMillis();
		LeitorPdf.Leitura leitura = new LeitorPdf("tesseract", "", 240).ler(pdf);
		long ms = System.currentTimeMillis() - inicio;

		InterpretadorDocumento interpretador = new InterpretadorDocumento(List.of("BRINCO", "ANEL", "COLAR",
				"PULSEIRA", "TORNOZELEIRA", "PINGENTE", "CONJUNTO", "KIT", "GARGANTILHA", "CHOKER", "BRI", "COL",
				"MALETA", "MOS0003", "TFB4110027", "FGF3060003", "FAB3060018", "INB4120004"));
		if (System.getenv("ITSJOIAS_DEPURAR") != null) {
			Files.write(Path.of(System.getenv("ITSJOIAS_DEPURAR"), "principal.txt"), leitura.principal());
			Files.write(Path.of(System.getenv("ITSJOIAS_DEPURAR"), "secundaria.txt"), leitura.secundaria());
		}
		DocumentoLido a = interpretador.interpretar(leitura.principal());
		DocumentoLido b = interpretador.interpretar(leitura.secundaria());
		List<Linha> linhas = MescladorLeituras.mesclar(a.linhas(), b.linhas());

		System.out.println("método: " + leitura.metodo() + " | páginas: " + leitura.paginas() + " | tempo: " + ms + " ms");
		System.out.println("cabeçalho: " + a.cabecalho());
		int validos = 0, invalidos = 0, semCodigo = 0, contaFecha = 0;
		for (Linha l : linhas) {
			Resultado r = l.codigo() == null ? null : CodigoBarras.conferir(l.codigo());
			if (r == null) semCodigo++; else if (r == Resultado.VALIDO) validos++; else invalidos++;
			if (Boolean.TRUE.equals(l.valoresBatem())) contaFecha++;
			System.out.printf("%-9s %-9s | %-60.60s | %3s x %8s = %8s%n", l.codigo(), r, l.descricao(),
					l.quantidade(), l.valorUnitario(), l.valorTotal());
		}
		long certosPag1 = linhas.stream().map(Linha::codigo).filter(c -> c != null && CODIGOS_PAGINA_1.contains(c)).distinct().count();
		long errosQuePassam = linhas.stream().map(Linha::codigo)
				.filter(c -> c != null && c.length() == 8 && CodigoBarras.conferir(c) == Resultado.VALIDO)
				.filter(c -> !CODIGOS_PAGINA_1.contains(c)).count();
		System.out.printf("linhas: %d | código válido: %d | inválido: %d | sem código: %d | conta fecha: %d%n",
				linhas.size(), validos, invalidos, semCodigo, contaFecha);
		System.out.printf("página 1: %d de %d códigos certos | válidos fora da pág. 1 (pág. 2 ou erro): %d%n",
				certosPag1, CODIGOS_PAGINA_1.size(), errosQuePassam);
		assertThat(linhas).hasSizeGreaterThan(50);
	}

}
