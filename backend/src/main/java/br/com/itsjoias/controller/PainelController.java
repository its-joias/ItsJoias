package br.com.itsjoias.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.service.PainelService;

@RestController
@RequestMapping("/api/painel")
public class PainelController {

	private final PainelService painelService;

	public PainelController(PainelService painelService) {
		this.painelService = painelService;
	}

	@GetMapping
	public PainelDTO painel() {
		return painelService.montarPainel();
	}

}
