package br.com.itsjoias.service;

import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.itsjoias.exception.NaoEncontradoException;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.repository.MaletaRepository;
import br.com.itsjoias.repository.MaletaRepository.MaletaTravada;
import br.com.itsjoias.repository.MaletaRepository.PecaDaMaleta;
import br.com.itsjoias.seguranca.UsuarioAtual;

/**
 * Descartar uma maleta importada errada, sem apagar nada (o banco não deixa apagar peças nem histórico):
 * as peças disponíveis saem do estoque como DEVOLUCAO_FORNECEDOR, a maleta fica ENCERRADA com o motivo,
 * e o código dela é liberado para importar o mesmo pedido de novo. Só sem peça vendida.
 */
@Service
public class MaletaService {

	private final MaletaRepository maletas;
	private final UsuarioAtual usuarioAtual;

	public MaletaService(MaletaRepository maletas, UsuarioAtual usuarioAtual) {
		this.maletas = maletas;
		this.usuarioAtual = usuarioAtual;
	}

	public record Descarte(long maletaId, String nome, int pecasRetiradas) {
	}

	@Transactional
	public Descarte descartar(long maletaId, String motivo) {
		String m = motivo == null ? "" : motivo.trim();
		if (m.length() < 3) {
			throw new RegraNegocioException("Escreva o motivo do descarte (ex.: importei o PDF errado).");
		}
		if (m.length() > 500) {
			throw new RegraNegocioException("O motivo pode ter no máximo 500 caracteres.");
		}
		MaletaTravada maleta = maletas.travar(maletaId).orElseThrow(() -> new NaoEncontradoException("Maleta não encontrada."));
		if (!"ATIVA".equals(maleta.status())) {
			throw new RegraNegocioException("Esta maleta já está encerrada.");
		}
		List<PecaDaMaleta> pecas = maletas.pecas(maletaId);
		long vendidas = pecas.stream().filter(p -> "VENDIDA".equals(p.situacao())).count();
		if (vendidas > 0) {
			throw new RegraNegocioException(vendidas == 1
					? "1 peça desta maleta está vendida. Cancele essa venda antes de descartar a maleta."
					: vendidas + " peças desta maleta estão vendidas. Cancele essas vendas antes de descartar a maleta.");
		}
		List<PecaDaMaleta> disponiveis = pecas.stream().filter(p -> "DISPONIVEL".equals(p.situacao())).toList();
		maletas.travarProdutos(new TreeSet<>(disponiveis.stream().map(PecaDaMaleta::produtoId).toList()));
		long usuario = usuarioAtual.obter().id();
		for (PecaDaMaleta peca : disponiveis) {
			maletas.devolverAoFornecedor(usuario, peca, "Maleta descartada: " + m);
		}
		// "PED43556" -> "X12-PED43556": o código original fica livre e o descartado continua único
		String novoCodigo = ("X" + maletaId + "-" + maleta.codigo());
		novoCodigo = novoCodigo.substring(0, Math.min(20, novoCodigo.length()));
		String novoNome = maleta.nome() + " (descartada)";
		novoNome = novoNome.substring(0, Math.min(120, novoNome.length()));
		maletas.encerrarComoDescartada(maletaId, novoCodigo, novoNome, m);
		return new Descarte(maletaId, maleta.nome(), disponiveis.size());
	}

}
