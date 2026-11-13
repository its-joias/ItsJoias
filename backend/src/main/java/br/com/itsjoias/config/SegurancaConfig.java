package br.com.itsjoias.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.itsjoias.repository.UsuarioRepository;
import br.com.itsjoias.seguranca.Perfil;
import br.com.itsjoias.seguranca.UsuarioAutenticacao;

/**
 * Quem pode chamar o quê.
 *
 * - Toda rota /api/** exige login, exceto entrar e renovar a sessão.
 * - O login é um token JWT emitido pelo Supabase Auth. O back confere a ASSINATURA do token
 *   (ninguém consegue inventar um), a validade e o emissor, e depois procura o usuário do
 *   ItsJoias ligado a ele para saber o perfil.
 * - Excluir (DELETE) é só para a PROPRIETARIA. Consultar, criar e editar: os dois perfis.
 * - Sem sessão no servidor (STATELESS): cada requisição traz o próprio token.
 */
@Configuration
public class SegurancaConfig {

	@Bean
	public SecurityFilterChain seguranca(HttpSecurity http, UsuarioRepository usuarios) throws Exception {
		String proprietaria = Perfil.PROPRIETARIA.name();
		String desenvolvimento = Perfil.DESENVOLVIMENTO.name();
		http
				.csrf(csrf -> csrf.disable()) // não há cookie de sessão: o token vai no cabeçalho
				.cors(cors -> { })
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rotas -> rotas
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/", "/error").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/entrar", "/api/auth/renovar").permitAll()
						.requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(proprietaria)
						.requestMatchers("/api/**").hasAnyRole(proprietaria, desenvolvimento)
						.anyRequest().denyAll())
				.oauth2ResourceServer(rs -> rs
						.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> new UsuarioAutenticacao(token,
								usuarioDoToken(usuarios, token.getSubject()))))
						.authenticationEntryPoint((req, res, e) -> responder(res, 401,
								"Sua sessão expirou ou você não entrou no sistema. Entre de novo.")))
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) -> responder(res, 401,
								"Sua sessão expirou ou você não entrou no sistema. Entre de novo."))
						.accessDeniedHandler((req, res, ex) -> responder(res, 403,
								"Seu perfil não tem permissão para esta ação.")));
		return http.build();
	}

	private static br.com.itsjoias.seguranca.UsuarioLogado usuarioDoToken(UsuarioRepository usuarios, String sub) {
		try {
			return usuarios.buscarPorLogin(UUID.fromString(sub)).orElse(null);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null; // token sem identificador válido: fica sem perfil e recebe 403
		}
	}

	private static void responder(jakarta.servlet.http.HttpServletResponse res, int status, String mensagem)
			throws java.io.IOException {
		res.setStatus(status);
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.getWriter().write("{\"mensagem\":\"" + mensagem.replace("\"", "\\\"") + "\"}");
	}

	/**
	 * Confere os tokens do Supabase. Projetos novos assinam com chave assimétrica e publicam
	 * as chaves públicas em /auth/v1/.well-known/jwks.json. Se o projeto usar o segredo antigo
	 * (HS256), basta definir SUPABASE_JWT_SECRET.
	 */
	@Bean
	public JwtDecoder jwtDecoder(@Value("${itsjoias.supabase.url:}") String url,
			@Value("${itsjoias.supabase.jwt-secret:}") String segredo) {
		String emissor = url + "/auth/v1";
		NimbusJwtDecoder decoder;
		if (!segredo.isBlank()) {
			decoder = NimbusJwtDecoder
					.withSecretKey(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
					.macAlgorithm(MacAlgorithm.HS256)
					.build();
		} else {
			decoder = NimbusJwtDecoder.withJwkSetUri(emissor + "/.well-known/jwks.json")
					.jwsAlgorithms(algs -> {
						algs.add(SignatureAlgorithm.ES256);
						algs.add(SignatureAlgorithm.RS256);
					})
					.build();
		}
		OAuth2TokenValidator<Jwt> publico = token -> token.getAudience() != null
				&& token.getAudience().contains("authenticated")
						? OAuth2TokenValidatorResult.success()
						: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token sem público 'authenticated'", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(emissor), publico));
		return decoder;
	}

	/**
	 * CORS: o navegador só deixa uma página chamar outra origem (o back no Render) se o back
	 * disser que aquela origem é confiável. Aqui ficam o site da Vercel, os previews dela
	 * e a máquina local.
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${itsjoias.cors.origens}") String origens) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOriginPatterns(Arrays.stream(origens.split(",")).map(String::trim)
				.filter(s -> !s.isEmpty()).toList());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
		cors.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/**", cors);
		return fonte;
	}

}
