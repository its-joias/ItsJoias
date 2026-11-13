package br.com.itsjoias.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O que entra e o que sai nas rotas de clientes (a entidade do banco não é exposta direto). */
public final class ClienteDTO {

	private ClienteDTO() {
	}

	public static final String FORMATO_TELEFONE = "^\\(\\d{2}\\) \\d{4,5}-\\d{4}$";
	/** Vazio é aceito (CPF é opcional). */
	public static final String FORMATO_CPF = "^$|^\\s*\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\s*$";

	/** Formulário de cadastro/edição. */
	public record ClienteForm(
			@NotBlank(message = "Informe o nome.")
			@Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
			String nome,

			@NotBlank(message = "Informe o telefone.")
			@Pattern(regexp = FORMATO_TELEFONE, message = "Use o formato (71) 99999-8888 ou (71) 3333-4444.")
			String telefone,

			@Email(message = "E-mail inválido.")
			@Size(max = 160, message = "O e-mail pode ter no máximo 160 caracteres.")
			String email,

			@Pattern(regexp = FORMATO_CPF, message = "Use o formato 123.456.789-01.")
			String cpf,

			@Size(max = 500, message = "O endereço pode ter no máximo 500 caracteres.")
			String endereco,

			@Size(max = 1000, message = "As observações podem ter no máximo 1000 caracteres.")
			String observacoes,

			/** Só na edição; no cadastro a cliente nasce ativa. */
			Boolean ativo) {

		/** Tira espaços das pontas e transforma campo opcional vazio em null. */
		public ClienteForm normalizado() {
			return new ClienteForm(limpar(nome), limpar(telefone), minusculo(limpar(email)), limpar(cpf),
					limpar(endereco), limpar(observacoes), ativo);
		}

		private static String limpar(String valor) {
			if (valor == null) {
				return null;
			}
			String v = valor.trim();
			return v.isEmpty() ? null : v;
		}

		private static String minusculo(String valor) {
			return valor == null ? null : valor.toLowerCase();
		}
	}

	/** Cliente com a situação financeira dela (vem de vw_saldo_clientes). */
	public record ClienteResposta(
			long id,
			String nome,
			String telefone,
			String email,
			String cpf,
			String endereco,
			String observacoes,
			boolean ativo,
			long quantidadeVendas,
			BigDecimal totalComprado,
			BigDecimal totalEmAberto,
			BigDecimal totalAtrasado,
			int maiorAtrasoDias) {
	}

}
