package br.com.itsjoias.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.com.itsjoias.exception.NaoEncontradoException;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.exception.TradutorErroBanco;

/**
 * Tratamento de erros em um lugar só. Toda resposta de erro tem o mesmo formato:
 * { "mensagem": "texto para a vendedora", "campos": { "telefone": "..." } }
 * ("campos" só aparece quando a validação de um formulário falha).
 */
@RestControllerAdvice
public class ErroHandler {

	private static final Logger log = LoggerFactory.getLogger(ErroHandler.class);

	public record ErroResposta(String mensagem, Map<String, String> campos) {
		public static ErroResposta de(String mensagem) {
			return new ErroResposta(mensagem, null);
		}
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException erro) {
		Map<String, String> campos = new LinkedHashMap<>();
		for (FieldError campo : erro.getBindingResult().getFieldErrors()) {
			campos.putIfAbsent(campo.getField(), campo.getDefaultMessage());
		}
		return ResponseEntity.badRequest()
				.body(new ErroResposta("Confira os campos destacados.", campos));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErroResposta> pedidoMalFormado(Exception erro) {
		return ResponseEntity.badRequest().body(ErroResposta.de("Os dados enviados estão em um formato inválido."));
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException erro) {
		return ResponseEntity.unprocessableContent().body(ErroResposta.de(erro.getMessage()));
	}

	@ExceptionHandler(NaoEncontradoException.class)
	public ResponseEntity<ErroResposta> naoEncontrado(NaoEncontradoException erro) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResposta.de(erro.getMessage()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException erro) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResposta.de("Endereço não encontrado."));
	}

	// Deixa a segurança do Spring responder 401/403 com o formato dela (ver SegurancaConfig)
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	public void seguranca(RuntimeException erro) {
		throw erro;
	}

	@ExceptionHandler({ CannotGetJdbcConnectionException.class, DataAccessResourceFailureException.class,
			CannotCreateTransactionException.class, QueryTimeoutException.class })
	public ResponseEntity<ErroResposta> bancoIndisponivel(Exception erro) {
		log.error("Banco indisponível", erro);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(ErroResposta.de("Não foi possível falar com o banco de dados. Tente de novo em instantes."));
	}

	/** Qualquer outro erro: se foi o banco recusando algo, mostra o motivo; senão, mensagem genérica. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErroResposta> inesperado(Exception erro) {
		Optional<String> recusaDoBanco = TradutorErroBanco.mensagem(erro);
		if (recusaDoBanco.isPresent()) {
			log.info("Banco recusou a operação: {}", recusaDoBanco.get());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(ErroResposta.de(recusaDoBanco.get()));
		}
		log.error("Erro inesperado", erro);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErroResposta.de("Aconteceu um erro inesperado. Tente de novo; se continuar, avise o suporte."));
	}

}
