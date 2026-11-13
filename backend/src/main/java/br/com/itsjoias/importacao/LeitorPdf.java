package br.com.itsjoias.importacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.itsjoias.exception.RegraNegocioException;

/**
 * Abre o PDF e devolve o texto de cada página, usando programas instalados no servidor:
 * - Poppler (pdfinfo, pdftotext, pdftoppm): lê o texto de PDFs digitais e transforma páginas em imagem;
 * - Tesseract: OCR (reconhecimento de texto em imagem) para PDFs escaneados/fotografados.
 *
 * PDF digital: o texto sai direto e exato. PDF escaneado: cada página passa pelo OCR DUAS vezes
 * (imagem em 300 e em 200 DPI); o MescladorLeituras junta as duas leituras.
 */
@Component
public class LeitorPdf {

	private static final Logger log = LoggerFactory.getLogger(LeitorPdf.class);

	/** Página com menos letras que isso é considerada imagem (escaneada). */
	private static final int MINIMO_DE_LETRAS_POR_PAGINA = 40;
	public static final int MAXIMO_DE_PAGINAS = 30;
	private static final Pattern PAGINAS = Pattern.compile("(?m)^Pages:\\s+(\\d+)");
	private static final Pattern CRIPTOGRAFADO = Pattern.compile("(?m)^Encrypted:\\s+yes");

	public enum Metodo {
		TEXTO, OCR
	}

	/** principal / secundaria: linhas do documento inteiro em cada leitura. No PDF digital, as duas são iguais. */
	public record Leitura(Metodo metodo, int paginas, List<String> principal, List<String> secundaria) {
	}

	private final String tesseract;
	private final String poppler;
	private final long limiteSegundos;

	public LeitorPdf(@Value("${itsjoias.ocr.tesseract:tesseract}") String tesseract,
			@Value("${itsjoias.ocr.poppler-dir:}") String popplerDir,
			@Value("${itsjoias.ocr.limite-segundos-por-pagina:240}") long limiteSegundos) {
		this.tesseract = tesseract;
		this.poppler = popplerDir.isBlank() ? "" : popplerDir.endsWith("/") ? popplerDir : popplerDir + "/";
		this.limiteSegundos = limiteSegundos;
	}

	public Leitura ler(byte[] pdf) {
		if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF")) {
			throw new RegraNegocioException("Este arquivo não é um PDF. Escolha o PDF da maleta.");
		}
		Path pasta = null;
		try {
			pasta = Files.createTempDirectory("itsjoias-pdf-");
			Path arquivo = pasta.resolve("maleta.pdf");
			Files.write(arquivo, pdf);

			Resultado info = rodar(List.of(poppler + "pdfinfo", arquivo.toString()), null, "pdfinfo");
			if (info.codigo() != 0) {
				throw new RegraNegocioException("Não foi possível abrir o PDF: o arquivo parece estar corrompido ou incompleto.");
			}
			String textoInfo = info.texto();
			if (CRIPTOGRAFADO.matcher(textoInfo).find()) {
				throw new RegraNegocioException("O PDF está protegido por senha. Salve uma cópia sem senha e tente de novo.");
			}
			Matcher m = PAGINAS.matcher(textoInfo);
			int paginas = m.find() ? Integer.parseInt(m.group(1)) : 0;
			if (paginas == 0) {
				throw new RegraNegocioException("O PDF não tem nenhuma página.");
			}
			if (paginas > MAXIMO_DE_PAGINAS) {
				throw new RegraNegocioException("O PDF tem " + paginas + " páginas; o máximo é " + MAXIMO_DE_PAGINAS + ".");
			}

			List<String> texto = new ArrayList<>();
			boolean digital = true;
			for (int p = 1; p <= paginas && digital; p++) {
				Resultado r = rodar(List.of(poppler + "pdftotext", "-layout", "-enc", "UTF-8", "-f", "" + p, "-l", "" + p,
						arquivo.toString(), "-"), null, "pdftotext");
				if (r.codigo() != 0) {
					throw new RegraNegocioException("Não foi possível ler a página " + p + " do PDF.");
				}
				if (r.texto().replaceAll("[^A-Za-zÀ-ÿ]", "").length() < MINIMO_DE_LETRAS_POR_PAGINA) {
					digital = false;
				} else {
					texto.addAll(r.texto().lines().toList());
				}
			}
			if (digital) {
				return new Leitura(Metodo.TEXTO, paginas, texto, texto);
			}
			return lerComOcr(arquivo, pasta, paginas);
		} catch (IOException e) {
			log.error("Falha ao ler PDF", e);
			throw new RegraNegocioException("Não foi possível ler o PDF. Tente de novo.");
		} finally {
			apagar(pasta);
		}
	}

	private Leitura lerComOcr(Path arquivo, Path pasta, int paginas) throws IOException {
		List<String> principal = new ArrayList<>();
		List<String> secundaria = new ArrayList<>();
		for (int p = 1; p <= paginas; p++) {
			principal.addAll(ocr(imagem(arquivo, pasta, p, 300), p));
			secundaria.addAll(ocr(imagem(arquivo, pasta, p, 200), p));
		}
		String tudo = String.join("", principal) + String.join("", secundaria);
		if (tudo.replaceAll("[^A-Za-zÀ-ÿ]", "").length() < MINIMO_DE_LETRAS_POR_PAGINA) {
			throw new RegraNegocioException("Não foi possível reconhecer texto nas páginas do PDF. "
					+ "Confira se a foto está nítida e bem iluminada, ou peça o PDF original ao fornecedor.");
		}
		return new Leitura(Metodo.OCR, paginas, principal, secundaria);
	}

	/** Página -> PNG em tons de cinza na resolução pedida. */
	private Path imagem(Path arquivo, Path pasta, int pagina, int dpi) throws IOException {
		String prefixo = pasta.resolve("p" + pagina + "-" + dpi).toString();
		Resultado r = rodar(List.of(poppler + "pdftoppm", "-r", "" + dpi, "-gray", "-png", "-singlefile",
				"-f", "" + pagina, "-l", "" + pagina, arquivo.toString(), prefixo), null, "pdftoppm");
		Path png = Path.of(prefixo + ".png");
		if (r.codigo() != 0 || !Files.exists(png)) {
			throw new RegraNegocioException("Não foi possível transformar a página " + pagina + " em imagem para leitura.");
		}
		return png;
	}

	/** Tesseract em português, bloco de texto uniforme, um processador. */
	private List<String> ocr(Path png, int pagina) throws IOException {
		Resultado r = rodar(List.of(tesseract, png.toString(), "stdout", "-l", "por", "--psm", "6"), "1", "tesseract");
		if (r.codigo() != 0) {
			throw new RegraNegocioException("Não foi possível ler a página " + pagina + " do PDF.");
		}
		Files.deleteIfExists(png);
		return r.texto().lines().toList();
	}

	private record Resultado(int codigo, String texto) {
	}

	private Resultado rodar(List<String> comando, String limiteThreads, String nome) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(comando);
		if (limiteThreads != null) {
			pb.environment().put("OMP_THREAD_LIMIT", limiteThreads);
		}
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		Process processo;
		try {
			processo = pb.start();
		} catch (IOException e) {
			log.error("Programa {} não encontrado no servidor", nome, e);
			throw new RegraNegocioException("O leitor de PDF não está disponível no servidor (" + nome + "). Avise o suporte.");
		}
		try {
			byte[] saida = processo.getInputStream().readAllBytes();
			if (!processo.waitFor(limiteSegundos, TimeUnit.SECONDS)) {
				processo.destroyForcibly();
				throw new RegraNegocioException("A leitura do PDF demorou demais. Tente de novo.");
			}
			return new Resultado(processo.exitValue(), new String(saida, StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			processo.destroyForcibly();
			throw new RegraNegocioException("A leitura do PDF foi interrompida. Tente de novo.");
		}
	}

	private static void apagar(Path pasta) {
		if (pasta == null) {
			return;
		}
		try (Stream<Path> arquivos = Files.walk(pasta)) {
			arquivos.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException e) {
			log.warn("Não foi possível apagar a pasta temporária {}", pasta);
		}
	}

}
