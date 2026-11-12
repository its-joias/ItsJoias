package br.com.itsjoias.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.service.PainelService;

@RestController
@RequestMapping("/api/painel")
@CrossOrigin(origins = "*")
public class PainelController {

	private static final Logger log = LoggerFactory.getLogger(PainelController.class);

	private final PainelService painelService;

	public PainelController(PainelService painelService) {
		this.painelService = painelService;
	}

	@GetMapping
	public PainelDTO painel() {
		return painelService.montarPainel();
	}

	/** Banco fora do ar ou consulta com erro: devolve uma mensagem que o front pode mostrar. */
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<Map<String, String>> erroNoBanco(DataAccessException erro) {
		log.error("Falha ao montar o painel", erro);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("mensagem", "Não foi possível ler os dados do banco. Tente de novo em instantes."));
	}

}
