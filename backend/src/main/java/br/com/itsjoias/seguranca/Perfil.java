package br.com.itsjoias.seguranca;

/**
 * Perfis de acesso (coluna usuarios.perfil).
 * PROPRIETARIA: tudo, inclusive excluir.
 * DESENVOLVIMENTO: consulta, cria e edita; não exclui nem administra usuários.
 */
public enum Perfil {
	PROPRIETARIA, DESENVOLVIMENTO;

	/** Nome do papel no Spring Security (hasRole("PROPRIETARIA") procura "ROLE_PROPRIETARIA"). */
	public String papel() {
		return "ROLE_" + name();
	}
}
