package br.com.itsjoias.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.itsjoias.config.SegurancaConfig;
import br.com.itsjoias.repository.UsuarioRepository;
import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.SupabaseAuthClient;
import br.com.itsjoias.seguranca.SupabaseAuthClient.Sessao;
import br.com.itsjoias.seguranca.SupabaseAuthClient.UsuarioSupabase;
import br.com.itsjoias.seguranca.UsuarioAtual;
import br.com.itsjoias.seguranca.UsuarioAutenticacao;
import br.com.itsjoias.seguranca.UsuarioLogado;
import br.com.itsjoias.service.ClienteService;

/**
 * Quem pode o quê, com o filtro de segurança de verdade (só os serviços são simulados).
 */
@WebMvcTest(controllers = { ClienteController.class, AuthController.class })
@Import({ SegurancaConfig.class, ErroHandler.class, UsuarioAtual.class })
@TestPropertySource(properties = "itsjoias.supabase.url=https://exemplo.supabase.co")
class ApiSegurancaTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ClienteService clienteService;

	@MockitoBean
	private UsuarioRepository usuarios;

	@MockitoBean
	private SupabaseAuthClient supabase;

	static RequestPostProcessor como(Perfil perfil) {
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(UUID.randomUUID().toString()).build();
		UsuarioLogado u = perfil == null ? null : new UsuarioLogado(1, "Teste", "t@example.com", perfil);
		return authentication(new UsuarioAutenticacao(jwt, u));
	}

	@Test
	void semLoginRecebe401ComMensagem() throws Exception {
		mvc.perform(get("/api/clientes"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Entre de novo")));
	}

	@Test
	void loginSemPerfilNoItsJoiasRecebe403() throws Exception {
		mvc.perform(get("/api/clientes").with(como(null))).andExpect(status().isForbidden());
	}

	@Test
	void desenvolvimentoConsultaMasNaoExclui() throws Exception {
		given(clienteService.listar(any(), org.mockito.ArgumentMatchers.anyBoolean())).willReturn(List.of());
		mvc.perform(get("/api/clientes").with(como(Perfil.DESENVOLVIMENTO))).andExpect(status().isOk());
		mvc.perform(delete("/api/clientes/7").with(como(Perfil.DESENVOLVIMENTO)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensagem").value("Seu perfil não tem permissão para esta ação."));
	}

	@Test
	void proprietariaExclui() throws Exception {
		mvc.perform(delete("/api/clientes/7").with(como(Perfil.PROPRIETARIA))).andExpect(status().isNoContent());
		verify(clienteService).excluir(7);
	}

	@Test
	void formularioInvalidoDiz400ECampo() throws Exception {
		mvc.perform(post("/api/clientes").with(como(Perfil.PROPRIETARIA)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\",\"telefone\":\"71999998888\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.nome").value("Informe o nome."))
				.andExpect(jsonPath("$.campos.telefone").value("Use o formato (71) 99999-8888 ou (71) 3333-4444."));
	}

	@Test
	void entrarComLoginNaoLiberadoNoItsJoias() throws Exception {
		UUID login = UUID.randomUUID();
		given(supabase.entrar("leide@example.com", "segredo"))
				.willReturn(new Sessao("acesso", "renovacao", 1800000000L, new UsuarioSupabase(login, "leide@example.com")));
		given(usuarios.buscarPorLogin(login)).willReturn(Optional.empty());

		mvc.perform(post("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\" Leide@Example.com \",\"senha\":\"segredo\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("não foi liberado")));
		verify(supabase).sair("acesso");
	}

	@Test
	void entrarDevolveTokensEPerfil() throws Exception {
		UUID login = UUID.randomUUID();
		given(supabase.entrar("leide@example.com", "segredo"))
				.willReturn(new Sessao("acesso", "renovacao", 1800000000L, new UsuarioSupabase(login, "leide@example.com")));
		given(usuarios.buscarPorLogin(login))
				.willReturn(Optional.of(new UsuarioLogado(1, "Leide Farias", "leide@example.com", Perfil.PROPRIETARIA)));

		mvc.perform(post("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"leide@example.com\",\"senha\":\"segredo\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("acesso"))
				.andExpect(jsonPath("$.usuario.perfil").value("PROPRIETARIA"));
	}

	@Test
	void corsAceitaAVercelERecusaOutrosSites() throws Exception {
		mvc.perform(options("/api/clientes").header("Origin", "https://itsjoias.vercel.app")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://itsjoias.vercel.app"));
		mvc.perform(options("/api/clientes").header("Origin", "https://site-qualquer.com")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden());
	}

}
