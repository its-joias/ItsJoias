package br.com.itsjoias.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.controller.ErroHandler.ErroResposta;
import br.com.itsjoias.repository.UsuarioRepository;
import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.SupabaseAuthClient;
import br.com.itsjoias.seguranca.SupabaseAuthClient.FalhaLogin;
import br.com.itsjoias.seguranca.SupabaseAuthClient.Sessao;
import br.com.itsjoias.seguranca.UsuarioAtual;
import br.com.itsjoias.seguranca.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrar, renovar a sessão, sair e "quem sou eu".
 * O front só fala com o back; o back fala com o Supabase Auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final SupabaseAuthClient supabase;
	private final UsuarioRepository usuarios;
	private final UsuarioAtual usuarioAtual;

	public AuthController(SupabaseAuthClient supabase, UsuarioRepository usuarios, UsuarioAtual usuarioAtual) {
		this.supabase = supabase;
		this.usuarios = usuarios;
		this.usuarioAtual = usuarioAtual;
	}

	public record PedidoEntrar(@NotBlank(message = "Informe o e-mail.") String email,
			@NotBlank(message = "Informe a senha.") String senha) {
	}

	public record PedidoRenovar(@NotBlank String refreshToken) {
	}

	public record UsuarioResposta(String nome, String email, Perfil perfil) {
		static UsuarioResposta de(UsuarioLogado u) {
			return new UsuarioResposta(u.nome(), u.email(), u.perfil());
		}
	}

	/** expiraEm: segundos desde 1970 (o front renova a sessão antes disso). */
	public record SessaoResposta(String accessToken, String refreshToken, long expiraEm, UsuarioResposta usuario) {
	}

	@PostMapping("/entrar")
	public ResponseEntity<?> entrar(@Valid @RequestBody PedidoEntrar pedido) {
		return abrirSessao(() -> supabase.entrar(pedido.email().trim().toLowerCase(), pedido.senha()));
	}

	@PostMapping("/renovar")
	public ResponseEntity<?> renovar(@Valid @RequestBody PedidoRenovar pedido) {
		return abrirSessao(() -> supabase.renovar(pedido.refreshToken()));
	}

	@PostMapping("/sair")
	public ResponseEntity<Void> sair(@RequestHeader("Authorization") String autorizacao) {
		supabase.sair(autorizacao.replaceFirst("(?i)^Bearer ", ""));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/eu")
	public UsuarioResposta eu() {
		return UsuarioResposta.de(usuarioAtual.obter());
	}

	private ResponseEntity<?> abrirSessao(java.util.function.Supplier<Sessao> pedirSessao) {
		Sessao sessao;
		try {
			sessao = pedirSessao.get();
		} catch (FalhaLogin falha) {
			HttpStatus status = falha.credenciaisInvalidas() ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE;
			return ResponseEntity.status(status).body(ErroResposta.de(falha.getMessage()));
		}
		UUID login = sessao.usuario() == null ? null : sessao.usuario().id();
		UsuarioLogado usuario = login == null ? null : usuarios.buscarPorLogin(login).orElse(null);
		if (usuario == null) {
			// O login existe no Supabase, mas ninguém liberou esta pessoa no ItsJoias
			supabase.sair(sessao.accessToken());
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErroResposta.de(
					"Seu acesso ainda não foi liberado no ItsJoias. Fale com a proprietária do sistema."));
		}
		return ResponseEntity.ok(new SessaoResposta(sessao.accessToken(), sessao.refreshToken(), sessao.expiraEm(),
				UsuarioResposta.de(usuario)));
	}

}
