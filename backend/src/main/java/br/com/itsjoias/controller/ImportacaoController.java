package br.com.itsjoias.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.itsjoias.dto.ImportacaoDTO.HistoricoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.PedidoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.Resultado;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.service.ImportacaoService;

/**
 * Importação de maleta por PDF.
 * POST /api/importacoes/ler        (multipart, campo "arquivo") lê o PDF e devolve a prévia; NÃO grava
 * POST /api/importacoes/validar    confere a prévia corrigida; NÃO grava
 * POST /api/importacoes/confirmar  grava tudo numa transação
 * GET  /api/importacoes            histórico
 */
@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

	private final ImportacaoService service;

	public ImportacaoController(ImportacaoService service) {
		this.service = service;
	}

	@PostMapping(path = "/ler", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Previa ler(@RequestPart("arquivo") MultipartFile arquivo) throws IOException {
		if (arquivo == null || arquivo.isEmpty()) {
			throw new RegraNegocioException("Escolha o PDF da maleta.");
		}
		return service.ler(arquivo.getOriginalFilename(), arquivo.getBytes());
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
