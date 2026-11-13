package br.com.itsjoias.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.GraficoDTO.PecasDaCategoria;
import br.com.itsjoias.dto.GraficoDTO.RecebimentosDoMes;
import br.com.itsjoias.dto.GraficoDTO.VendasDoMes;
import br.com.itsjoias.repository.GraficoRepository;
import br.com.itsjoias.service.CalendarioCobranca;

/**
 * GET /api/graficos/vendas-por-mes        últimos 12 meses
 * GET /api/graficos/recebimentos-por-mes  5 meses antes até 6 meses depois (mês de vencimento das parcelas)
 * GET /api/graficos/pecas-por-categoria   peças das maletas ativas
 */
@RestController
@RequestMapping("/api/graficos")
@Transactional(readOnly = true)
public class GraficoController {

	private final GraficoRepository graficos;
	private final CalendarioCobranca calendario;

	public GraficoController(GraficoRepository graficos, CalendarioCobranca calendario) {
		this.graficos = graficos;
		this.calendario = calendario;
	}

	@GetMapping("/vendas-por-mes")
	public List<VendasDoMes> vendasPorMes() {
		return graficos.vendasPorMes(calendario.hoje(), 12);
	}

	@GetMapping("/recebimentos-por-mes")
	public List<RecebimentosDoMes> recebimentosPorMes() {
		return graficos.recebimentosPorMes(calendario.hoje(), 5, 6);
	}

	@GetMapping("/pecas-por-categoria")
	public List<PecasDaCategoria> pecasPorCategoria() {
		return graficos.pecasPorCategoria();
	}

}
