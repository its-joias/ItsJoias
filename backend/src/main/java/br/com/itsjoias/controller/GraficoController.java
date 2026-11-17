package br.com.itsjoias.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.RequestParam;
import br.com.itsjoias.dto.GraficoDTO.VendasDoDia;
import br.com.itsjoias.exception.RegraNegocioException;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.GraficoDTO.PecasDaCategoria;
import br.com.itsjoias.dto.GraficoDTO.RecebimentosDoMes;
import br.com.itsjoias.dto.GraficoDTO.VendasDoMes;
import br.com.itsjoias.dto.GraficoDTO.VendidoDaCategoria;
import br.com.itsjoias.repository.GraficoRepository;
import br.com.itsjoias.service.CalendarioCobranca;

/**
 * GET /api/graficos/vendas-por-mes        últimos 12 meses
 * GET /api/graficos/vendas-por-dia?mes=2026-11  cada dia de um mês (o detalhe de uma linha da tabela)
 * GET /api/graficos/recebimentos-por-mes  5 meses antes até 6 meses depois (mês de vencimento das parcelas)
 * GET /api/graficos/pecas-por-categoria   peças das maletas ativas
 * GET /api/graficos/vendido-por-categoria valor vendido por categoria, últimos 12 meses (pizza)
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

	@GetMapping("/vendas-por-dia")
	public List<VendasDoDia> vendasPorDia(@RequestParam String mes) {
		YearMonth m;
		try {
			m = YearMonth.parse(mes);
		} catch (DateTimeParseException e) {
			throw new RegraNegocioException("Mês inválido. Use o formato 2026-11.");
		}
		return graficos.vendasPorDia(m.atDay(1));
	}

	@GetMapping("/recebimentos-por-mes")
	public List<RecebimentosDoMes> recebimentosPorMes() {
		return graficos.recebimentosPorMes(calendario.hoje(), 5, 6);
	}

	@GetMapping("/vendido-por-categoria")
	public List<VendidoDaCategoria> vendidoPorCategoria() {
		return graficos.vendidoPorCategoria(calendario.hoje(), 12);
	}

	@GetMapping("/pecas-por-categoria")
	public List<PecasDaCategoria> pecasPorCategoria() {
		return graficos.pecasPorCategoria();
	}

}
