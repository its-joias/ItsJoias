package br.com.itsjoias.importacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Cabeçalho do termo (dados fictícios). */
class InterpretadorCabecalhoTest {

	private final InterpretadorDocumento interpretador = new InterpretadorDocumento(List.of("BRINCO"));

	@Test
	void letraSoltaDoOcrSaiDoNomeDoFornecedor() {
		var cab = interpretador.lerCabecalho(List.of("E Fornecedora Teste Semijoias Ltda.   CNPJ: 11.222.333/0001-81"));
		assertEquals("FORNECEDORA TESTE SEMIJOIAS LTDA.", cab.fornecedorNome());
		assertEquals("11.222.333/0001-81", cab.cnpj());
	}

	@Test
	void artigoNoComecoDoNomeEhMantido() {
		var cab = interpretador.lerCabecalho(List.of("A Joia Teste Ltda"));
		assertEquals("A JOIA TESTE LTDA", cab.fornecedorNome());
	}
}
