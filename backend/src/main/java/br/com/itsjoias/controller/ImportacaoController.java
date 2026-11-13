package br.com.itsjoias.controller;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.itsjoias.dto.ImportacaoDTO.HistoricoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.LeituraAndamento;
import br.com.itsjoias.dto.ImportacaoDTO.PedidoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.Resultado;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.service.ImportacaoService;
import br.com.itsjoias.service.LeiturasEmSegundoPlano;
import br.com.itsjoias.seguranca.UsuarioAtual;

/**
 * Importação de maleta por PDF.
 * POST /api/importacoes/ler        (multipart, campo "arquivo") começa a ler o PDF; NÃO grava.
 *                                  Devolve a situação: PRONTA (com a prévia) ou NA_FILA/LENDO (consultar depois)
 * GET  /api/importacoes/leituras/{id}  situação da leitura (PDF escaneado leva alguns minutos)
 * POST /api/importacoes/validar    confere a prévia corrigida; NÃO grava
 * POST /api/importacoes/confirmar  grava tudo numa transação
 * GET  /api/importacoes            histórico
 */
@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

	/** PDF digital fica pronto nesse tempo e já volta com a prévia, sem a tela precisar consultar. */
	private static final Duration ESPERA_INICIAL = Duration.ofSeconds(8);

	private final ImportacaoService service;
	private final LeiturasEmSegundoPlano leituras;
	private final UsuarioAtual usuarioAtual;

	public ImportacaoController(ImportacaoService service, LeiturasEmSegundoPlano leituras, UsuarioAtual usuarioAtual) {
		this.service = service;
		this.leituras = leituras;
		this.usuarioAtual = usuarioAtual;
	}

	@PostMapping(path = "/ler", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LeituraAndamento ler(@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
		if (arquivo == null || arquivo.isEmpty()) {
			throw new RegraNegocioException("Escolha o PDF da maleta.");
		}
		return leituras.iniciar(usuarioAtual.obter().id(), arquivo.getOriginalFilename(), arquivo.getBytes(),
				ESPERA_INICIAL);
	}

	@GetMapping("/leituras/{id}")
	public LeituraAndamento leitura(@PathVariable String id) {
		return leituras.consultar(usuarioAtual.obter().id(), id);
	}

	@PostMapping("/validar")
	public Previa validar(@RequestBody PedidoImportacao pedido) {
		return service.validar(pedido);
	}

	@PostMapping("/confirmar")
	public ResponseEntity<Resultado> confirmar(@RequestBody PedidoImportacao pedido) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.confirmar(pedido));
	}

	@GetMapping
	public List<HistoricoImportacao> historico(@RequestParam(defaultValue = "50") int limite) {
		return service.historico(limite);
	}

}
