package br.com.itsjoias.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.itsjoias.dto.ImportacaoDTO.LeituraAndamento;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.SituacaoLeitura;
import br.com.itsjoias.exception.NaoEncontradoException;
import br.com.itsjoias.exception.RegraNegocioException;
import jakarta.annotation.PreDestroy;

/**
 * Lê os PDFs em segundo plano.
 *
 * Por quê: no servidor gratuito, um PDF escaneado leva alguns minutos no OCR, e navegadores (principalmente
 * no celular) desistem de uma requisição que fica tanto tempo sem resposta. Então o PDF é recebido na hora,
 * a leitura entra numa fila e a tela consulta a situação a cada poucos segundos.
 *
 * - Uma leitura por vez (o OCR usa bastante memória; o servidor gratuito tem 512 MB).
 * - Cada pessoa só consulta as próprias leituras.
 * - Nada é gravado no banco aqui: a leitura só gera a prévia, que fica guardada na memória por 30 minutos.
 */
@Component
public class LeiturasEmSegundoPlano {

	private static final Logger log = LoggerFactory.getLogger(LeiturasEmSegundoPlano.class);
	private static final Duration VALIDADE = Duration.ofMinutes(30);
	private static final int MAXIMO_NA_FILA = 3;

	private final ImportacaoService service;
	private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "leitura-pdf");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, Leitura> leituras = new ConcurrentHashMap<>();

	public LeiturasEmSegundoPlano(ImportacaoService service) {
		this.service = service;
	}

	private static final class Leitura {
		final long usuarioId;
		final Instant inicio = Instant.now();
		volatile SituacaoLeitura situacao = SituacaoLeitura.NA_FILA;
		volatile Previa previa;
		volatile String mensagem;
		volatile Future<?> tarefa;

		Leitura(long usuarioId) {
			this.usuarioId = usuarioId;
		}
	}

	/**
	 * Coloca o PDF na fila e espera um pouco: PDF digital costuma ficar pronto nesse tempo e já volta com a prévia.
	 */
	public LeituraAndamento iniciar(long usuarioId, String nomeArquivo, byte[] conteudo, Duration esperar) {
		limparAntigas();
		long pendentes = leituras.values().stream()
				.filter(l -> l.situacao == SituacaoLeitura.NA_FILA || l.situacao == SituacaoLeitura.LENDO).count();
		if (pendentes >= MAXIMO_NA_FILA) {
			throw new RegraNegocioException("Já há PDFs sendo lidos. Aguarde terminar e tente de novo.");
		}
		String id = UUID.randomUUID().toString();
		Leitura leitura = new Leitura(usuarioId);
		leituras.put(id, leitura);
		leitura.tarefa = fila.submit(() -> executar(leitura, nomeArquivo, conteudo));
		try {
			leitura.tarefa.get(esperar.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException ainda) {
			// segue em segundo plano; a tela consulta depois
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			// executar() já registra o erro na leitura
		}
		return situacao(id, leitura);
	}

	public LeituraAndamento consultar(long usuarioId, String id) {
		limparAntigas();
		Leitura leitura = id == null ? null : leituras.get(id);
		if (leitura == null || leitura.usuarioId != usuarioId) {
			throw new NaoEncontradoException("Leitura não encontrada (ela expira depois de 30 minutos). Envie o PDF de novo.");
		}
		return situacao(id, leitura);
	}

	private void executar(Leitura leitura, String nomeArquivo, byte[] conteudo) {
		leitura.situacao = SituacaoLeitura.LENDO;
		try {
			leitura.previa = service.ler(nomeArquivo, conteudo);
			leitura.situacao = SituacaoLeitura.PRONTA;
		} catch (RegraNegocioException e) {
			leitura.mensagem = e.getMessage();
			leitura.situacao = SituacaoLeitura.ERRO;
		} catch (RuntimeException e) {
			log.error("Falha inesperada ao ler PDF", e);
			leitura.mensagem = "Não foi possível ler o PDF por um erro no servidor. Tente de novo em alguns minutos.";
			leitura.situacao = SituacaoLeitura.ERRO;
		}
	}

	private static LeituraAndamento situacao(String id, Leitura l) {
		long segundos = Duration.between(l.inicio, Instant.now()).toSeconds();
		return new LeituraAndamento(id, l.situacao, segundos, l.previa, l.mensagem);
	}

	private void limparAntigas() {
		Instant limite = Instant.now().minus(VALIDADE);
		leituras.values().removeIf(l -> l.inicio.isBefore(limite)
				&& (l.situacao == SituacaoLeitura.PRONTA || l.situacao == SituacaoLeitura.ERRO));
	}

	@PreDestroy
	void encerrar() {
		fila.shutdownNow();
	}

}
