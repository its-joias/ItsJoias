package br.com.itsjoias.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.PainelDTO.MaletaAtiva;
import br.com.itsjoias.repository.PainelRepository;
import br.com.itsjoias.service.MaletaService;
import br.com.itsjoias.service.MaletaService.Descarte;

/**
 * GET  /api/maletas                 maletas ativas com peças e valores (mesma consulta do Painel)
 * POST /api/maletas/{id}/descartar  descarta uma maleta importada errada (só PROPRIETARIA; sem peça vendida)
 */
@RestController
@RequestMapping("/api/maletas")
public class MaletaController {

	private final PainelRepository painelRepository;
	private final MaletaService maletaService;

	public MaletaController(PainelRepository painelRepository, MaletaService maletaService) {
		this.painelRepository = painelRepository;
		this.maletaService = maletaService;
	}

	public record DescarteForm(String motivo) {
	}

	@PostMapping("/{id}/descartar")
	public Descarte descartar(@PathVariable long id, @RequestBody DescarteForm form) {
		return maletaService.descartar(id, form.motivo());
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<MaletaAtiva> ativas() {
		return painelRepository.buscarMaletasAtivas();
	}

}
