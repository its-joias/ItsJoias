package br.com.itsjoias.seguranca;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Conversa com o Supabase Auth (o serviço de login do Supabase) pela API HTTP dele.
 * Só usa a chave PÚBLICA (publishable): ela identifica o projeto e pode ficar no servidor
 * sem risco. A senha da vendedora passa por aqui e vai direto para o Supabase; o ItsJoias
 * nunca guarda senha.
 */
@Component
public class SupabaseAuthClient {

	private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);

	private final RestClient http;
	private final boolean configurado;

	public SupabaseAuthClient(@Value("${itsjoias.supabase.url:}") String url,
			@Value("${itsjoias.supabase.chave-publica:}") String chavePublica) {
		this.configurado = !url.isBlank() && !chavePublica.isBlank();
		// corpo "bufferizado": a requisição sai com Content-Length (alguns servidores recusam envio em pedaços)
		this.http = RestClient.builder()
				.requestFactory(new BufferingClientHttpRequestFactory(new JdkClientHttpRequestFactory()))
				.baseUrl(url.isBlank() ? "http://supabase-nao-configurado" : url + "/auth/v1")
				.defaultHeader("apikey", chavePublica)
				.build();
	}

	/** Resposta do Supabase Auth com os tokens da sessão. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Sessao(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("expires_at") long expiraEm,
			@JsonProperty("user") UsuarioSupabase usuario) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record UsuarioSupabase(UUID id, String email) {
	}

	/** Falha de login com o motivo já em português. */
	public static class FalhaLogin extends RuntimeException {
		private final boolean credenciaisInvalidas;

		FalhaLogin(String mensagem, boolean credenciaisInvalidas) {
			super(mensagem);
			this.credenciaisInvalidas = credenciaisInvalidas;
		}

		public boolean credenciaisInvalidas() {
			return credenciaisInvalidas;
		}
	}

	public Sessao entrar(String email, String senha) {
		return pedirToken("password", Map.of("email", email, "password", senha));
	}

	public Sessao renovar(String refreshToken) {
		return pedirToken("refresh_token", Map.of("refresh_token", refreshToken));
	}

	/** Encerra a sessão no Supabase (o refresh token deixa de valer). Falhas são só registradas. */
	public void sair(String accessToken) {
		if (!configurado) {
			return;
		}
		try {
			http.post().uri("/logout").header("Authorization", "Bearer " + accessToken).retrieve().toBodilessEntity();
		} catch (RuntimeException e) {
			log.info("Logout no Supabase não confirmado: {}", e.getMessage());
		}
	}

	private Sessao pedirToken(String tipo, Map<String, String> corpo) {
		if (!configurado) {
			throw new FalhaLogin("O login ainda não foi configurado no servidor (SUPABASE_URL e SUPABASE_PUBLISHABLE_KEY).", false);
		}
		try {
			return http.post().uri(u -> u.path("/token").queryParam("grant_type", tipo).build())
					.contentType(MediaType.APPLICATION_JSON)
					.body(corpo)
					.retrieve()
					.body(Sessao.class);
		} catch (RestClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			if (status.value() == 400 || status.value() == 401) {
				boolean refresh = "refresh_token".equals(tipo);
				throw new FalhaLogin(refresh ? "Sua sessão expirou. Entre de novo."
						: "E-mail ou senha incorretos.", true);
			}
			if (status.value() == 429) {
				throw new FalhaLogin("Muitas tentativas seguidas. Aguarde um minuto e tente de novo.", false);
			}
			log.error("Supabase Auth respondeu {}: {}", status, e.getResponseBodyAsString());
			throw new FalhaLogin("O serviço de login respondeu com erro. Tente de novo em instantes.", false);
		} catch (ResourceAccessException e) {
			log.error("Supabase Auth inacessível", e);
			throw new FalhaLogin("Não foi possível falar com o serviço de login. Tente de novo em instantes.", false);
		}
	}

}
