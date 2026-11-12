package br.com.itsjoias.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.VendaDTO.PecaResumo;
import br.com.itsjoias.dto.VendaDTO.Previa;
import br.com.itsjoias.dto.VendaDTO.VendaCriada;
import br.com.itsjoias.dto.VendaDTO.VendaForm;
import br.com.itsjoias.dto.VendaDTO.VendaResumo;
import br.com.itsjoias.service.VendaService;
import jakarta.validation.Valid;

/**
 * POST /api/vendas/previa   calcula total e parcelas, sem gravar
 * POST /api/vendas          grava a venda (FINALIZADA), baixa as peças, cria parcelas e pagamentos na hora
 * GET  /api/vendas          vendas recentes (?clienteId=)
 * GET  /api/pecas           busca peças (?busca=C0001&situacao=DISPONIVEL)
 */
@RestController
@RequestMapping("/api")
public class VendaController {

	private final VendaService vendaService;

	public VendaController(VendaService vendaService) {
		this.vendaService = vendaService;
	}

	@PostMapping("/vendas/previa")
	public Previa previa(@Valid @RequestBody VendaForm form) {
		return vendaService.previa(form);
	}

	@PostMapping("/vendas")
	public ResponseEntity<VendaCriada> confirmar(@Valid @RequestBody VendaForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(vendaService.confirmar(form));
	}

	@GetMapping("/vendas")
	public List<VendaResumo> recentes(@RequestParam(required = false) Long clienteId,
			@RequestParam(defaultValue = "30") int limite) {
		return vendaService.recentes(clienteId, limite);
	}

	@GetMapping("/pecas")
	public List<PecaResumo> pecas(@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "DISPONIVEL") String situacao,
			@RequestParam(defaultValue = "30") int limite) {
		return vendaService.buscarPecas(busca, "TODAS".equalsIgnoreCase(situacao) ? null : situacao.toUpperCase(), limite);
	}

}
