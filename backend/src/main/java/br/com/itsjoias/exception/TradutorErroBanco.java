package br.com.itsjoias.exception;

import java.util.Map;
import java.util.Optional;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Traduz um erro do PostgreSQL numa mensagem que a vendedora entende.
 *
 * O banco é a "rede de segurança" do sistema (constraints e triggers). Quando ele recusa
 * algo, o erro chega aqui com o nome da regra; este tradutor troca o nome técnico
 * por uma frase em português.
 */
public final class TradutorErroBanco {

	/** Nome da constraint -> mensagem. */
	private static final Map<String, String> MENSAGENS = Map.ofEntries(
			Map.entry("ck_clientes_telefone", "Telefone inválido. Use o formato (71) 99999-8888 ou (71) 3333-4444."),
			Map.entry("ck_clientes_cpf", "CPF inválido. Use o formato 123.456.789-01."),
			Map.entry("ck_clientes_nome", "Informe o nome da cliente."),
			Map.entry("uq_clientes_cpf", "Já existe uma cliente cadastrada com este CPF."),
			Map.entry("vendas_cliente_id_fkey",
					"Esta cliente tem vendas registradas e não pode ser excluída. Você pode desativá-la."),
			Map.entry("cobrancas_cliente_id_fkey",
					"Esta cliente tem cobranças registradas e não pode ser excluída. Você pode desativá-la."));

	private TradutorErroBanco() {
	}

	/** Procura um erro do PostgreSQL na cadeia de causas. */
	public static Optional<ServerErrorMessage> erroDoBanco(Throwable erro) {
		for (Throwable t = erro; t != null; t = t.getCause()) {
			if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
				return Optional.of(psql.getServerErrorMessage());
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return Optional.empty();
	}

	/**
	 * Mensagem para o usuário, se o erro for uma recusa do banco (constraint ou trigger).
	 * Vazio quando é outro tipo de falha (ex.: banco fora do ar).
	 */
	public static Optional<String> mensagem(Throwable erro) {
		return erroDoBanco(erro).flatMap(TradutorErroBanco::traduzir);
	}

	private static Optional<String> traduzir(ServerErrorMessage m) {
		String estado = m.getSQLState() == null ? "" : m.getSQLState();
		if (m.getConstraint() != null && MENSAGENS.containsKey(m.getConstraint())) {
			return Optional.of(MENSAGENS.get(m.getConstraint()));
		}
		return switch (estado) {
			// RAISE EXCEPTION das triggers: as mensagens já foram escritas em português no banco
			case "P0001" -> Optional.ofNullable(m.getMessage());
			case "23505" -> Optional.of("Já existe um registro com estes dados.");
			case "23503" -> Optional.of("Este registro está ligado a outros e não pode ser alterado ou excluído.");
			case "23514", "23502" -> Optional.of("Algum campo não passou nas regras do banco. Confira os dados.");
			default -> Optional.empty();
		};
	}

}
