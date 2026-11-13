package br.com.itsjoias.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.UsuarioLogado;

/** Liga o login do Supabase Auth (auth.users.id) ao usuário do ItsJoias (public.usuarios). */
@Repository
public class UsuarioRepository {

	private final JdbcTemplate jdbc;

	public UsuarioRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Usuário ATIVO ligado a este login, ou vazio se o acesso não foi liberado. */
	public Optional<UsuarioLogado> buscarPorLogin(UUID authUserId) {
		List<UsuarioLogado> encontrados = jdbc.query(
				"SELECT id, nome, email, perfil FROM usuarios WHERE auth_user_id = ? AND ativo",
				(rs, i) -> new UsuarioLogado(rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
						Perfil.valueOf(rs.getString("perfil"))),
				authUserId);
		return encontrados.stream().findFirst();
	}

}
