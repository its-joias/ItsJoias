package br.com.itsjoias.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.PainelDTO.MaletaAtiva;
import br.com.itsjoias.repository.PainelRepository;

/**
 * GET /api/maletas: maletas ativas com peças e valores (mesma consulta do Painel).
 * O cadastro de entrada de maleta pelo sistema é a próxima etapa.
 */
@RestController
@RequestMapping("/api/maletas")
public class MaletaController {

	private final PainelRepository painelRepository;

	public MaletaController(PainelRepository painelRepository) {
		this.painelRepository = painelRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<MaletaAtiva> ativas() {
		return painelRepository.buscarMaletasAtivas();
	}

}
