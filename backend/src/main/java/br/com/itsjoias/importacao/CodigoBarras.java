package br.com.itsjoias.importacao;

/**
 * Dígito verificador de códigos de barras (EAN-8, UPC-A de 12 e EAN-13).
 *
 * O último dígito desses códigos é calculado a partir dos outros. Se o OCR ler um dígito errado,
 * a conta quase sempre deixa de fechar, e o sistema avisa "confira este código".
 */
public final class CodigoBarras {

	public enum Resultado {
		/** O dígito verificador confere. */
		VALIDO,
		/** O dígito verificador NÃO confere: provável erro de leitura ou digitação. */
		INVALIDO,
		/** Tamanho sem dígito verificador conhecido (não dá para conferir). */
		SEM_VERIFICACAO
	}

	private CodigoBarras() {
	}

	public static Resultado conferir(String codigo) {
		if (codigo == null || !codigo.matches("\\d+")) {
			return Resultado.INVALIDO;
		}
		int n = codigo.length();
		if (n != 8 && n != 12 && n != 13) {
			return Resultado.SEM_VERIFICACAO;
		}
		// Da direita para a esquerda (sem o verificador), os pesos alternam 3, 1, 3, 1...
		int soma = 0;
		for (int i = n - 2, peso = 3; i >= 0; i--, peso = peso == 3 ? 1 : 3) {
			soma += (codigo.charAt(i) - '0') * peso;
		}
		int verificador = (10 - soma % 10) % 10;
		return verificador == codigo.charAt(n - 1) - '0' ? Resultado.VALIDO : Resultado.INVALIDO;
	}

}
