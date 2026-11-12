package br.com.itsjoias.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.dto.PainelDTO.AReceber;
import br.com.itsjoias.dto.PainelDTO.Atrasado;
import br.com.itsjoias.dto.PainelDTO.ClientesDevendo;
import br.com.itsjoias.dto.PainelDTO.ProximoDia5;
import br.com.itsjoias.dto.PainelDTO.Recebido;
import br.com.itsjoias.dto.PainelDTO.Totais;
import br.com.itsjoias.dto.PainelDTO.TotalVendido;
import br.com.itsjoias.service.PainelService;

@WebMvcTest(PainelController.class)
class PainelControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PainelService painelService;

	@Test
	void devolveDinheiroComDuasCasasEDatasIso() throws Exception {
		LocalDate dia5 = LocalDate.of(2026, 12, 5);
		given(painelService.montarPainel()).willReturn(new PainelDTO(
				LocalDate.of(2026, 11, 11),
				new Totais(
						new TotalVendido(new BigDecimal("1958.40"), 4),
						new Recebido(new BigDecimal("1000.00"), new BigDecimal("51.1")),
						new AReceber(new BigDecimal("958.40"), 7),
						new Atrasado(new BigDecimal("0.00"), 0),
						new ClientesDevendo(3, 0),
						new ProximoDia5(dia5, new BigDecimal("300.00"), 3)),
				List.of(), List.of(), List.of()));

		mvc.perform(get("/api/painel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hoje").value("2026-11-11"))
				.andExpect(jsonPath("$.totais.proximoDia5.data").value("2026-12-05"))
				.andExpect(content().string(Matchers.containsString("\"valor\":1958.40")))
				.andExpect(content().string(Matchers.containsString("\"valor\":0.00")));
	}

	@Test
	void bancoForaDoArDevolve503ComMensagem() throws Exception {
		given(painelService.montarPainel()).willThrow(new DataAccessResourceFailureException("conexão recusada"));

		mvc.perform(get("/api/painel"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.mensagem").value(Matchers.startsWith("Não foi possível ler os dados")));
	}

}
