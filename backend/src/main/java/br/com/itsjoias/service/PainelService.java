package br.com.itsjoias.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.itsjoias.dto.PainelDTO;
import br.com.itsjoias.repository.PainelRepository;

/**
 * Monta o Painel da vendedora. Fixa "hoje" (em São Paulo) e o próximo dia 5 uma vez só,
 * e as quatro consultas rodam na mesma transação de leitura (REPEATABLE READ): os totais
 * e as listas enxergam o mesmo retrato do banco, mesmo que entre um pagamento no meio.
 */
@Service
public class PainelService {

	private final PainelRepository painelRepository;
	private final CalendarioCobranca calendario;

	public PainelService(PainelRepository painelRepository, CalendarioCobranca calendario) {
		this.painelRepository = painelRepository;
		this.calendario = calendario;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PainelDTO montarPainel() {
		LocalDate hoje = calendario.hoje();
		LocalDate proximoDia5 = CalendarioCobranca.proximoDia5(hoje);

		return new PainelDTO(
				hoje,
				painelRepository.buscarTotais(hoje, proximoDia5),
				painelRepository.buscarParcelasAtrasadas(hoje),
				painelRepository.buscarParcelasQueVencemEm(proximoDia5),
				painelRepository.buscarMaletasAtivas());
	}

}
