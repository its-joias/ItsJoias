package br.com.itsjoias.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import br.com.itsjoias.dto.ImportacaoDTO.LeituraAndamento;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.SituacaoLeitura;
import br.com.itsjoias.exception.NaoEncontradoException;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.service.ImportacaoService;
import br.com.itsjoias.service.LeiturasEmSegundoPlano;

/** Fila de leitura de PDF em segundo plano (o serviço de leitura é simulado). */
class LeiturasEmSegundoPlanoTest {

	private static final byte[] PDF = "%PDF-teste".getBytes();
	private final ImportacaoService service = mock(ImportacaoService.class);
	private final LeiturasEmSegundoPlano leituras = new LeiturasEmSegundoPlano(service);
	private final Previa previa = mock(Previa.class);

	@Test
	void pdfRapidoJaVoltaComAPrevia() {
		when(service.ler(eq("a.pdf"), any())).thenReturn(previa);
		LeituraAndamento r = leituras.iniciar(1, "a.pdf", PDF, Duration.ofSeconds(5));
		assertThat(r.situacao()).isEqualTo(SituacaoLeitura.PRONTA);
		assertThat(r.previa()).isSameAs(previa);
	}

	@Test
	void pdfDemoradoSegueEmSegundoPlanoEDepoisFicaPronto() throws Exception {
		CountDownLatch libera = new CountDownLatch(1);
		when(service.ler(eq("lento.pdf"), any())).thenAnswer(i -> {
			libera.await(5, TimeUnit.SECONDS);
			return previa;
		});
		LeituraAndamento r = leituras.iniciar(1, "lento.pdf", PDF, Duration.ofMillis(100));
		assertThat(r.situacao()).isIn(SituacaoLeitura.NA_FILA, SituacaoLeitura.LENDO);
		assertThat(r.previa()).isNull();

		libera.countDown();
		LeituraAndamento depois = esperarFim(1, r.id());
		assertThat(depois.situacao()).isEqualTo(SituacaoLeitura.PRONTA);
		assertThat(depois.previa()).isSameAs(previa);
	}

	@Test
	void erroDeLeituraVoltaComAMensagem() {
		when(service.ler(eq("ruim.pdf"), any())).thenThrow(new RegraNegocioException("Este arquivo não é um PDF."));
		LeituraAndamento r = leituras.iniciar(1, "ruim.pdf", PDF, Duration.ofSeconds(5));
		assertThat(r.situacao()).isEqualTo(SituacaoLeitura.ERRO);
		assertThat(r.mensagem()).isEqualTo("Este arquivo não é um PDF.");
	}

	@Test
	void erroInesperadoNaoMostraDetalheTecnico() {
		when(service.ler(eq("bug.pdf"), any())).thenThrow(new IllegalStateException("NullPointer em algum lugar"));
		LeituraAndamento r = leituras.iniciar(1, "bug.pdf", PDF, Duration.ofSeconds(5));
		assertThat(r.situacao()).isEqualTo(SituacaoLeitura.ERRO);
		assertThat(r.mensagem()).startsWith("Não foi possível ler o PDF").doesNotContain("NullPointer");
	}

	@Test
	void umaPessoaNaoConsultaALeituraDeOutra() {
		when(service.ler(eq("a.pdf"), any())).thenReturn(previa);
		LeituraAndamento r = leituras.iniciar(1, "a.pdf", PDF, Duration.ofSeconds(5));
		assertThat(leituras.consultar(1, r.id()).situacao()).isEqualTo(SituacaoLeitura.PRONTA);
		assertThatThrownBy(() -> leituras.consultar(2, r.id())).isInstanceOf(NaoEncontradoException.class);
		assertThatThrownBy(() -> leituras.consultar(1, "nao-existe")).isInstanceOf(NaoEncontradoException.class);
	}

	private LeituraAndamento esperarFim(long usuario, String id) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			LeituraAndamento r = leituras.consultar(usuario, id);
			if (r.situacao() == SituacaoLeitura.PRONTA || r.situacao() == SituacaoLeitura.ERRO) {
				return r;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("a leitura não terminou");
	}
}
