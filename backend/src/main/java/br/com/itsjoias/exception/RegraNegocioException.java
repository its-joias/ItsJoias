package br.com.itsjoias.exception;

/**
 * Pedido que não pode ser atendido por uma regra do negócio
 * (ex.: peça já vendida, entrada maior que o total). Vira HTTP 422 com a mensagem.
 */
public class RegraNegocioException extends RuntimeException {

	public RegraNegocioException(String mensagem) {
		super(mensagem);
	}

}
