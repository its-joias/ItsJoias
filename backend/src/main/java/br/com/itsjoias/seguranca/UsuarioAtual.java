package br.com.itsjoias.seguranca;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Dá acesso ao usuário logado na requisição atual (para gravar quem fez a venda, o pagamento etc.). */
@Component
public class UsuarioAtual {

	public UsuarioLogado obter() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof UsuarioAutenticacao u && u.usuario() != null) {
			return u.usuario();
		}
		throw new AccessDeniedException("Usuário não identificado.");
	}

}
