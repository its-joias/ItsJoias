package br.com.itsjoias.importacao;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera um PDF DIGITAL simples (texto de verdade, não imagem) só para os testes.
 * Cada string vira uma linha; "\n" quebra a página. Fonte Helvetica, acentos em WinAnsi.
 */
public final class PdfDeTeste {

	private static final Charset WIN_ANSI = Charset.forName("windows-1252");

	private PdfDeTeste() {
	}

	public static byte[] comLinhas(List<String> linhas) {
		List<List<String>> paginas = new ArrayList<>();
		List<String> atual = new ArrayList<>();
		for (String l : linhas) {
			if (l.equals("\f")) {
				paginas.add(atual);
				atual = new ArrayList<>();
			} else {
				atual.add(l);
			}
		}
		paginas.add(atual);

		List<byte[]> objetos = new ArrayList<>();
		int n = paginas.size();
		// 1 catálogo, 2 páginas, 3 fonte, depois (página, conteúdo) para cada página
		StringBuilder kids = new StringBuilder();
		for (int i = 0; i < n; i++) {
			kids.append(4 + i * 2).append(" 0 R ");
		}
		objetos.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
		objetos.add(ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>"));
		objetos.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
		for (int i = 0; i < n; i++) {
			ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
			escrever(conteudo, ascii("BT /F1 9 Tf 11 TL 30 810 Td\n"));
			for (String linha : paginas.get(i)) {
				escrever(conteudo, ascii("("));
				escrever(conteudo, escapar(linha).getBytes(WIN_ANSI));
				escrever(conteudo, ascii(") Tj T*\n"));
			}
			escrever(conteudo, ascii("ET"));
			byte[] stream = conteudo.toByteArray();
			objetos.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> "
					+ "/Contents " + (5 + i * 2) + " 0 R >>"));
			ByteArrayOutputStream obj = new ByteArrayOutputStream();
			escrever(obj, ascii("<< /Length " + stream.length + " >>\nstream\n"));
			escrever(obj, stream);
			escrever(obj, ascii("\nendstream"));
			objetos.add(obj.toByteArray());
		}

		ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		escrever(pdf, ascii("%PDF-1.4\n"));
		List<Integer> posicoes = new ArrayList<>();
		for (int i = 0; i < objetos.size(); i++) {
			posicoes.add(pdf.size());
			escrever(pdf, ascii((i + 1) + " 0 obj\n"));
			escrever(pdf, objetos.get(i));
			escrever(pdf, ascii("\nendobj\n"));
		}
		int xref = pdf.size();
		StringBuilder tabela = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
		for (int p : posicoes) {
			tabela.append(String.format("%010d 00000 n \n", p));
		}
		tabela.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
				.append(xref).append("\n%%EOF\n");
		escrever(pdf, ascii(tabela.toString()));
		return pdf.toByteArray();
	}

	private static String escapar(String s) {
		return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
	}

	private static byte[] ascii(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	private static void escrever(ByteArrayOutputStream saida, byte[] bytes) {
		saida.write(bytes, 0, bytes.length);
	}

}
