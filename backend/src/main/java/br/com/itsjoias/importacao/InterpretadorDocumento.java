package br.com.itsjoias.importacao;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.com.itsjoias.importacao.DocumentoLido.Cabecalho;
import br.com.itsjoias.importacao.DocumentoLido.Linha;

/**
 * Transforma as linhas de texto de um documento de entrega (termo, romaneio, nota) em cabeçalho + peças.
 *
 * Não depende de um layout fixo: uma linha de peça é reconhecida por ter VALOR EM REAIS e, de
 * preferência, um CÓDIGO numérico no começo. O que vem entre os dois é a descrição; o número logo
 * antes do primeiro valor é a quantidade. Linhas sem valor são pedaços de descrição quebrada.
 * Tolera o "ruído" típico de OCR: bordas de tabela lidas como | [ ], vírgula que some (R$7900),
 * marcas de caneta junto do código.
 */
public class InterpretadorDocumento {

	private static final Pattern DINHEIRO = Pattern.compile("[Rn]\\s?\\$\\s?(\\d[\\d.,]*\\d|\\d)");
	private static final Pattern CODIGO = Pattern.compile("(?<![\\d.,/])(\\d{6,14})(?![\\d.,/])");
	private static final Pattern CNPJ = Pattern.compile("(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})");
	private static final Pattern PEDIDO = Pattern.compile("PEDIDO\\s*N\\S{0,2}\\s*:?\\s*(\\d{3,12})");
	private static final Pattern DATA_ACERTO = Pattern.compile("DATA\\s*(?:DE\\s*)?ACERTO\\D{0,3}(\\d{2}/\\d{2}/\\d{4})");
	private static final Pattern DATA_CRIACAO = Pattern.compile("DATA\\s*(?:DE\\s*)?(?:CRIACAO|EMISSAO|ENTREGA)\\D{0,3}(\\d{2}/\\d{2}/\\d{4})");
	private static final Pattern TOTAL_ITENS = Pattern.compile("VALOR\\s*TOTAL\\s*(?:DOS\\s*)?ITENS\\D{0,8}(\\d[\\d.,]*\\d)");
	private static final Pattern EMPRESA = Pattern.compile("([A-Z0-9][A-Z0-9 &.\\-]{2,60}?\\b(?:LTDA|EIRELI|S/?A|ME|EPP)\\b\\.?)");
	/** Referência do fornecedor no começo da descrição, ex.: "TFB4110027 - BRI SJ OURO...". */
	private static final Pattern REFERENCIA = Pattern.compile("[A-Z]{2,4}\\s?\\d{4,8}\\s*-\\s");
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Palavras que abrem a descrição de uma peça (BRINCO, ANEL, COLAR...), sem acento e em maiúsculas. */
	private final Set<String> palavrasDeAbertura;

	public InterpretadorDocumento(Collection<String> palavrasDeAbertura) {
		this.palavrasDeAbertura = palavrasDeAbertura.stream().map(InterpretadorDocumento::normalizar)
				.collect(Collectors.toUnmodifiableSet());
	}

	public DocumentoLido interpretar(List<String> linhasTexto) {
		Cabecalho cabecalho = lerCabecalho(linhasTexto);
		List<Linha> itens = new ArrayList<>();
		List<String> pendentes = new ArrayList<>(); // pedaços de descrição esperando a linha certa
		boolean naTabela = false;

		for (String bruta : linhasTexto) {
			String linha = limpar(bruta);
			String norm = normalizar(linha);
			if (naTabela && fimDosItens(norm)) {
				break;
			}
			if (marcaDagua(norm)) {
				linha = linha.replaceAll("(?i)scanned\\s*with.*$", "").trim();
				norm = normalizar(linha);
			}
			if (!naTabela && norm.contains("DESCRI") && (norm.contains("QTD") || norm.contains("VALOR"))) {
				naTabela = true; // linha de títulos da tabela
				continue;
			}
			Linha item = lerItem(linha, bruta);
			if (item == null) {
				if (naTabela) {
					String fragmento = limparDescricao(linha);
					if (fragmento.replaceAll("[^A-Za-zÀ-ÿ]", "").length() >= 3) {
						pendentes.add(fragmento);
					}
				}
				continue;
			}
			if (!naTabela && item.codigo() == null) {
				continue; // valor solto no cabeçalho (ex.: endereço com número), não é peça
			}
			naTabela = true;
			item = encaixarPendentes(item, pendentes, itens);
			pendentes.clear();
			itens.add(item);
		}
		if (!pendentes.isEmpty() && !itens.isEmpty()) {
			int ultimo = itens.size() - 1;
			itens.set(ultimo, itens.get(ultimo).comDescricao(juntar(itens.get(ultimo).descricao(), pendentes)));
		}
		return new DocumentoLido(cabecalho, itens);
	}

	// ---- cabeçalho -------------------------------------------------------------------------

	Cabecalho lerCabecalho(List<String> linhas) {
		String tudo = normalizar(String.join("\n", linhas));
		String empresa = null;
		for (String l : linhas) {
			Matcher m = EMPRESA.matcher(normalizar(l));
			if (m.find()) {
				// letra solta no começo é sujeira do OCR ("E IT STORE..."); "A" e "O" podem ser artigo do nome
				empresa = m.group(1).trim().replaceFirst("^(?:[^AO\\s]\\s+)+", "");
				break;
			}
		}
		return new Cabecalho(empresa, primeiro(CNPJ, tudo), primeiro(PEDIDO, tudo),
				data(primeiro(DATA_CRIACAO, tudo)), data(primeiro(DATA_ACERTO, tudo)),
				dinheiro(primeiro(TOTAL_ITENS, tudo)));
	}

	private static boolean fimDosItens(String norm) {
		return norm.contains("TOTAL ITENS") || norm.contains("DESCONTO TOTAL") || norm.contains("SUBCATEG")
				|| norm.startsWith("TOTAL GERAL");
	}

	/** Marca d'água de aplicativos de digitalização: não é conteúdo do documento. */
	private static boolean marcaDagua(String norm) {
		return norm.contains("SCANNED") || norm.contains("CAMSCAN") || norm.contains("CAMSCANNER");
	}

	// ---- uma linha de peça ---------------------------------------------------------------

	/** Linha de peça, ou null se a linha não tem nenhum valor em reais. */
	Linha lerItem(String linha, String original) {
		Matcher m = DINHEIRO.matcher(linha);
		List<BigDecimal> valores = new ArrayList<>();
		int inicioValores = -1;
		while (m.find()) {
			BigDecimal v = dinheiro(m.group(1));
			if (v != null) {
				if (inicioValores < 0) {
					inicioValores = m.start();
				}
				valores.add(v);
			}
		}
		if (valores.isEmpty()) {
			return null;
		}
		String antes = linha.substring(0, inicioValores).trim();

		// código: o primeiro número de 6 a 14 dígitos antes da descrição
		String codigo = null;
		Matcher c = CODIGO.matcher(antes);
		if (c.find() && c.start() <= Math.max(12, antes.length() / 3)) {
			codigo = c.group(1);
			antes = (antes.substring(0, c.start()) + " " + antes.substring(c.end())).trim();
		}

		// quantidade: o número inteiro logo antes do primeiro valor
		Integer quantidade = null;
		String[] tokens = antes.split("\\s+");
		if (tokens.length > 1 && tokens[tokens.length - 1].matches("\\d{1,3}")) {
			quantidade = Integer.valueOf(tokens[tokens.length - 1]);
			antes = antes.substring(0, antes.lastIndexOf(tokens[tokens.length - 1])).trim();
		}

		BigDecimal unitario = valores.get(0);
		BigDecimal total = null;
		if (valores.size() >= 3) {
			total = valores.get(2);                       // unitário | desconto | total
		} else if (valores.size() == 2 && valores.get(1).signum() != 0) {
			total = valores.get(1);                       // unitário | total
		}
		boolean deduzida = false;
		if ((quantidade == null || quantidade == 0) && total != null && unitario.signum() > 0) {
			BigDecimal[] div = total.divideAndRemainder(unitario);
			if (div[1].signum() == 0 && div[0].signum() > 0 && div[0].compareTo(BigDecimal.valueOf(999)) <= 0) {
				quantidade = div[0].intValueExact();
				deduzida = true;
			}
		}
		String descricao = limparDescricao(antes);
		return new Linha(codigo, descricao.isEmpty() ? null : descricao, quantidade, unitario, total, deduzida,
				original.trim());
	}

	/**
	 * Descrição quebrada em duas linhas: o pedaço que começa com BRINCO/ANEL... vai para a FRENTE da
	 * linha seguinte (que então começa no meio da frase); os demais completam a linha anterior.
	 */
	private Linha encaixarPendentes(Linha item, List<String> pendentes, List<Linha> anteriores) {
		if (pendentes.isEmpty()) {
			return item;
		}
		boolean itemComecaBem = comecaComPalavraDeAbertura(item.descricao());
		List<String> paraAnterior = new ArrayList<>();
		List<String> paraEste = new ArrayList<>();
		boolean jogandoParaEste = false;
		for (String p : pendentes) {
			if (!itemComecaBem && (jogandoParaEste || comecaComPalavraDeAbertura(p))) {
				jogandoParaEste = true;
				paraEste.add(p);
			} else {
				paraAnterior.add(p);
			}
		}
		if (!paraAnterior.isEmpty() && !anteriores.isEmpty()) {
			int i = anteriores.size() - 1;
			anteriores.set(i, anteriores.get(i).comDescricao(juntar(anteriores.get(i).descricao(), paraAnterior)));
		} else if (!paraAnterior.isEmpty()) {
			paraEste.addAll(0, paraAnterior);
		}
		if (paraEste.isEmpty()) {
			return item;
		}
		return item.comDescricao(juntar(String.join(" ", paraEste), List.of(item.descricao() == null ? "" : item.descricao())));
	}

	public boolean comecaComPalavraDeAbertura(String descricao) {
		if (descricao == null || descricao.isBlank()) {
			return false;
		}
		String norm = normalizar(descricao).trim();
		if (REFERENCIA.matcher(norm).lookingAt()) {
			return true;
		}
		String primeira = norm.split("[^A-Z0-9]+")[0];
		return palavrasDeAbertura.contains(primeira);
	}

	private static String juntar(String base, List<String> partes) {
		StringBuilder sb = new StringBuilder(base == null ? "" : base);
		for (String p : partes) {
			if (!p.isBlank()) {
				sb.append(sb.length() == 0 ? "" : " ").append(p.trim());
			}
		}
		return sb.toString().replaceAll("\\s+", " ").trim();
	}

	// ---- limpeza de texto ---------------------------------------------------------------

	/** Bordas de tabela e caixinhas viram espaço. */
	static String limpar(String linha) {
		return linha.replaceAll("[|\\[\\]{}“”\"]", " ").replaceAll("\\s+", " ").trim();
	}

	/** Tira o lixo das pontas (marcas de caneta, letras soltas do OCR). */
	String limparDescricao(String texto) {
		// números longos (códigos de barras repetidos ou da linha vizinha) não fazem parte da descrição
		String t = texto.replaceAll("(?<![A-Za-z0-9])\\d{6,14}[,.]?(?![A-Za-z0-9])", " ").replaceAll("\\s+", " ").trim();
		// começo com lixo ("Q/ANEL", "? ) BRINCO"): corta na palavra de abertura MAIS PRÓXIMA do início.
		// Se a descrição já começa certa (BRINCO..., TFB4110027 - ...), não mexe.
		String semPontas = t.replaceFirst("^[^A-Za-zÀ-ÿ0-9]+", "");
		if (!comecaComPalavraDeAbertura(semPontas)) {
			String norm = normalizar(t);
			int corte = -1;
			for (String palavra : palavrasDeAbertura) {
				Matcher m = Pattern.compile("(?:^|[^A-Z])(" + Pattern.quote(palavra) + ")\\b").matcher(norm);
				if (m.find() && m.start(1) <= 20 && (corte < 0 || m.start(1) < corte)) {
					corte = m.start(1);
				}
			}
			if (corte > 0) {
				t = t.substring(corte);
			}
		}
		// pontas: tokens sem letras/dígitos úteis ou "palavras" curtas com minúsculas (ruído do OCR)
		List<String> tokens = new ArrayList<>(List.of(t.split(" ")));
		while (!tokens.isEmpty() && ruido(tokens.get(0))) {
			tokens.remove(0);
		}
		while (!tokens.isEmpty() && ruidoNoFim(tokens.get(tokens.size() - 1))) {
			tokens.remove(tokens.size() - 1);
		}
		return String.join(" ", tokens).trim();
	}

	private static boolean ruido(String token) {
		if (token.isBlank() || !token.matches(".*[A-Za-zÀ-ÿ0-9].*")) {
			return true; // só símbolos
		}
		if (token.endsWith("...") || token.endsWith("…")) {
			return false; // fim de descrição cortada pelo próprio documento
		}
		return token.length() <= 2 && !token.equals(token.toUpperCase(Locale.ROOT));
	}

	/** No FIM da descrição, sílabas soltas de 1-2 letras são ruído do OCR (o "1" da quantidade lido como "EN"). */
	private static boolean ruidoNoFim(String token) {
		return ruido(token) || token.matches("[A-Za-zÀ-ÿ]{1,2}");
	}

	// ---- conversões -------------------------------------------------------------------

	/** "1.263,00", "45,00", "7900" (vírgula perdida no OCR) -> BigDecimal com 2 casas. */
	static BigDecimal dinheiro(String texto) {
		if (texto == null) {
			return null;
		}
		String t = texto.trim();
		String numero;
		if (t.contains(",")) {
			numero = t.replace(".", "").replace(",", ".");
		} else if (t.matches("\\d{3,9}")) {
			numero = t.substring(0, t.length() - 2) + "." + t.substring(t.length() - 2);
		} else if (t.matches("\\d{1,3}(\\.\\d{3})+")) {
			numero = t.replace(".", "");
		} else if (t.matches("\\d+\\.\\d{2}")) {
			numero = t;
		} else {
			return null;
		}
		try {
			BigDecimal v = new BigDecimal(numero);
			return v.scale() <= 2 ? v.setScale(2) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static LocalDate data(String texto) {
		if (texto == null) {
			return null;
		}
		try {
			return LocalDate.parse(texto, DATA);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String primeiro(Pattern p, String texto) {
		Matcher m = p.matcher(texto);
		return m.find() ? m.group(1) : null;
	}

	/** Maiúsculas e sem acento: "Criação" -> "CRIACAO". */
	public static String normalizar(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
	}

}
