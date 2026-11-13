package br.com.itsjoias.seguranca;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Autenticação de uma requisição: o token (JWT) do Supabase já conferido + o usuário do ItsJoias.
 * Sem usuário (login existe no Supabase, mas não foi liberado no ItsJoias) não há papel
 * nenhum, e todas as rotas protegidas respondem 403.
 */
public class UsuarioAutenticacao extends AbstractAuthenticationToken {

	private final Jwt token;
	private final transient UsuarioLogado usuario;

	public UsuarioAutenticacao(Jwt token, UsuarioLogado usuario) {
		super(usuario == null ? List.of() : List.of(new SimpleGrantedAuthority(usuario.perfil().papel())));
		this.token = token;
		this.usuario = usuario;
		setAuthenticated(true);
	}

	public UsuarioLogado usuario() {
		return usuario;
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public Object getPrincipal() {
		return usuario != null ? usuario : token.getSubject();
	}

	@Override
	public String getName() {
		return usuario != null ? usuario.email() : token.getSubject();
	}

}
