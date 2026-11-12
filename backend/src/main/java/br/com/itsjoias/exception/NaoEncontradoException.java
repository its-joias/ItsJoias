package br.com.itsjoias.exception;

/** O registro pedido não existe. Vira HTTP 404 com a mensagem. */
public class NaoEncontradoException extends RuntimeException {

	public NaoEncontradoException(String mensagem) {
		super(mensagem);
	}

}
