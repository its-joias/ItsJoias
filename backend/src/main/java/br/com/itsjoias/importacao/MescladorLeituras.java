package br.com.itsjoias.importacao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import br.com.itsjoias.importacao.CodigoBarras.Resultado;
import br.com.itsjoias.importacao.DocumentoLido.Linha;

/**
 * Junta duas leituras de OCR da mesma página (ex.: imagem em 300 e em 200 DPI).
 *
 * Cada leitura erra em lugares diferentes. Para cada peça da leitura principal, procura a mesma peça
 * na outra (pela descrição) e aproveita o que lá estiver melhor:
 * - o código, quando o da principal está ausente ou não passa no dígito verificador;
 * - quantidade e valores, quando os da principal não fecham a conta (qtd × unitário ≠ total).
 * Peças que só a outra leitura achou (com código válido) entram também.
 */
public final class MescladorLeituras {

	private static final double SEMELHANCA_MINIMA = 0.5;

	private MescladorLeituras() {
	}

	public static List<Linha> mesclar(List<Linha> principal, List<Linha> outra) {
		List<Linha> resultado = new ArrayList<>();
		boolean[] usada = new boolean[outra.size()];
		int[] parDe = new int[principal.size()];

		for (int i = 0; i < principal.size(); i++) {
			Linha a = principal.get(i);
			int j = melhorPar(a, outra, usada);
			parDe[i] = j;
			if (j < 0) {
				resultado.add(a);
				continue;
			}
			usada[j] = true;
			Linha b = outra.get(j);
			Linha m = a;
			if (!codigoBom(a.codigo()) && codigoBom(b.codigo())) {
				m = m.comCodigo(b.codigo());
			} else if (a.codigo() == null && b.codigo() != null) {
				m = m.comCodigo(b.codigo());
			}
			if (!Boolean.TRUE.equals(a.valoresBatem()) && Boolean.TRUE.equals(b.valoresBatem())) {
				m = m.comValores(b.quantidade(), b.valorUnitario(), b.valorTotal(), b.quantidadeDeduzida());
			}
			if (m.descricao() == null && b.descricao() != null) {
				m = m.comDescricao(b.descricao());
			}
			resultado.add(m);
		}

		// peças que só a outra leitura achou: entram na posição relativa certa
		Set<String> codigos = new HashSet<>();
		resultado.forEach(l -> codigos.add(l.codigo()));
		for (int j = 0; j < outra.size(); j++) {
			Linha b = outra.get(j);
			if (usada[j] || !codigoBom(b.codigo()) || codigos.contains(b.codigo())) {
				continue;
			}
			// antes de acrescentar: é a mesma peça de uma linha vizinha cujo código não foi lido direito?
			int vizinha = vizinhaSemCodigoBom(resultado, b, (double) j / Math.max(1, outra.size()));
			if (vizinha >= 0) {
				resultado.set(vizinha, resultado.get(vizinha).comCodigo(b.codigo()));
				codigos.add(b.codigo());
				continue;
			}
			int posicao = resultado.size();
			for (int i = parDe.length - 1; i >= 0; i--) {
				if (parDe[i] >= 0 && parDe[i] < j) {
					posicao = posicaoNoResultado(resultado, principal.get(i)) + 1;
					break;
				}
				if (i == 0) {
					posicao = 0;
				}
			}
			resultado.add(Math.min(posicao, resultado.size()), b);
			codigos.add(b.codigo());
		}
		return resultado;
	}

	/**
	 * Linha da leitura principal, perto da mesma posição relativa, com o mesmo preço e sem código válido:
	 * é quase certamente a mesma peça (a principal só não conseguiu ler o código).
	 */
	private static int vizinhaSemCodigoBom(List<Linha> resultado, Linha b, double posicaoRelativa) {
		int centro = (int) Math.round(posicaoRelativa * resultado.size());
		int melhor = -1;
		double melhorNota = 0.15;
		for (int k = Math.max(0, centro - 4); k <= Math.min(resultado.size() - 1, centro + 4); k++) {
			Linha a = resultado.get(k);
			if (codigoBom(a.codigo()) || a.valorUnitario() == null || !a.valorUnitario().equals(b.valorUnitario())) {
				continue;
			}
			double nota = semelhanca(a.descricao(), b.descricao()) + 0.2 - Math.abs(k - centro) * 0.02;
			if (nota > melhorNota) {
				melhorNota = nota;
				melhor = k;
			}
		}
		return melhor;
	}

	private static int posicaoNoResultado(List<Linha> resultado, Linha original) {
		for (int k = 0; k < resultado.size(); k++) {
			if (resultado.get(k).textoOriginal().equals(original.textoOriginal())) {
				return k;
			}
		}
		return resultado.size() - 1;
	}

	private static boolean codigoBom(String codigo) {
		return codigo != null && CodigoBarras.conferir(codigo) == Resultado.VALIDO;
	}

	private static int melhorPar(Linha a, List<Linha> outra, boolean[] usada) {
		int melhor = -1;
		double nota = SEMELHANCA_MINIMA;
		for (int j = 0; j < outra.size(); j++) {
			if (usada[j]) {
				continue;
			}
			Linha b = outra.get(j);
			double s = semelhanca(a.descricao(), b.descricao());
			if (a.codigo() != null && a.codigo().equals(b.codigo())) {
				s += 0.5; // mesmo código lido nas duas: com certeza é a mesma peça
			}
			if (a.valorUnitario() != null && a.valorUnitario().equals(b.valorUnitario())) {
				s += 0.2; // mesmo preço reforça
			}
			if (s > nota) {
				nota = s;
				melhor = j;
			}
		}
		return melhor;
	}

	/** Quantas palavras (de 3+ letras) as duas descrições têm em comum, de 0 a 1. */
	static double semelhanca(String x, String y) {
		Set<String> a = palavras(x);
		Set<String> b = palavras(y);
		if (a.isEmpty() || b.isEmpty()) {
			return 0;
		}
		Set<String> comum = new HashSet<>(a);
		comum.retainAll(b);
		Set<String> todas = new HashSet<>(a);
		todas.addAll(b);
		return (double) comum.size() / todas.size();
	}

	private static Set<String> palavras(String texto) {
		Set<String> s = new HashSet<>();
		if (texto != null) {
			for (String p : InterpretadorDocumento.normalizar(texto).split("[^A-Z0-9]+")) {
				if (p.length() >= 3) {
					s.add(p);
				}
			}
		}
		return s;
	}

}
