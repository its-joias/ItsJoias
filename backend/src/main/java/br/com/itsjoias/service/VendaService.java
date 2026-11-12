package br.com.itsjoias.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.itsjoias.dto.VendaDTO.FormaVenda;
import br.com.itsjoias.dto.VendaDTO.ItemPrevia;
import br.com.itsjoias.dto.VendaDTO.ParcelaPrevia;
import br.com.itsjoias.dto.VendaDTO.PecaResumo;
import br.com.itsjoias.dto.VendaDTO.Previa;
import br.com.itsjoias.dto.VendaDTO.VendaCriada;
import br.com.itsjoias.dto.VendaDTO.VendaForm;
import br.com.itsjoias.dto.VendaDTO.VendaResumo;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.repository.VendaRepository;
import br.com.itsjoias.repository.VendaRepository.PecaParaVenda;
import br.com.itsjoias.seguranca.UsuarioAtual;

/**
 * Venda: prévia (só calcula, não grava) e confirmação (grava tudo numa transação).
 *
 * Cada código digitado pode ser:
 * - a ETIQUETA de uma peça (C0001-02): vende exatamente aquela peça;
 * - o código do MODELO (C0001): vende a peça disponível da maleta mais antiga (FIFO).
 */
@Service
public class VendaService {

	private static final Pattern ETIQUETA = Pattern.compile("^([A-Z]{1,3}[0-9]{4,})-([0-9]{1,4})$");
	private static final Pattern MODELO = Pattern.compile("^[A-Z]{1,3}[0-9]{4,}$");

	private final VendaRepository vendas;
	private final CalendarioCobranca calendario;
	private final UsuarioAtual usuarioAtual;

	public VendaService(VendaRepository vendas, CalendarioCobranca calendario, UsuarioAtual usuarioAtual) {
		this.vendas = vendas;
		this.calendario = calendario;
		this.usuarioAtual = usuarioAtual;
	}

	/** O que a vendedora digitou, já interpretado. */
	private record Pedido(String digitado, Long pecaId, long produtoId) {
	}

	@Transactional(readOnly = true)
	public Previa previa(VendaForm form) {
		return montar(form, false);
	}

	@Transactional
	public VendaCriada confirmar(VendaForm form) {
		long usuario = usuarioAtual.obter().id();
		Previa previa = montar(form, true);

		long vendaId = vendas.inserirVenda(previa.clienteId(), usuario, previa.dataVenda(),
				previa.valorProdutos(), previa.acrescimo());
		for (ItemPrevia item : previa.itens()) {
			vendas.inserirItemEBaixa(vendaId, usuario, vendas.peca(item.pecaId()));
		}
		for (ParcelaPrevia parcela : previa.parcelas()) {
			long parcelaId = vendas.inserirParcela(vendaId, parcela.numero(), parcela.valor(), parcela.vencimento());
			if (parcela.pagaNaVenda()) {
				vendas.inserirPagamento(parcelaId, usuario, parcela.valor(), previa.dataVenda(), form.formaPagamento());
			}
		}
		// As triggers conferem no COMMIT: itens = valor_produtos, parcelas = total, baixa de cada peça.
		return new VendaCriada(vendaId, previa);
	}

	@Transactional(readOnly = true)
	public List<VendaResumo> recentes(Long clienteId, int limite) {
		return vendas.listarRecentes(clienteId, Math.clamp(limite, 1, 200));
	}

	@Transactional(readOnly = true)
	public List<PecaResumo> buscarPecas(String busca, String situacao, int limite) {
		return vendas.buscarPecas(busca, situacao, Math.clamp(limite, 1, 200));
	}

	// ------------------------------------------------------------------------------------------

	private Previa montar(VendaForm form, boolean travar) {
		String cliente = vendas.nomeClienteAtiva(form.clienteId())
				.orElseThrow(() -> new RegraNegocioException("Cliente não encontrada ou desativada."));
		validarPagamentoNaHora(form);

		List<Pedido> pedidos = interpretar(form.codigos());
		if (travar) {
			vendas.travarProdutos(new TreeSet<>(pedidos.stream().map(Pedido::produtoId).toList()));
		}
		List<PecaParaVenda> pecas = escolherPecas(pedidos);

		List<ItemPrevia> itens = pecas.stream().map(p -> new ItemPrevia(p.pecaId(), p.etiqueta(), p.produtoNome(),
				p.categoria(), p.maleta(), p.precoVenda())).toList();
		BigDecimal valorProdutos = pecas.stream().map(PecaParaVenda::precoVenda).reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2);
		BigDecimal acrescimo = CalculadoraVenda.acrescimo(valorProdutos, form.acrescimo10());
		BigDecimal total = valorProdutos.add(acrescimo);
		LocalDate hoje = calendario.hoje();
		List<ParcelaPrevia> parcelas = CalculadoraVenda.parcelas(hoje, total, form.forma(), form.numeroParcelas(),
				form.forma() == FormaVenda.PARCELADO ? form.entrada() : null);
		BigDecimal pagoAgora = parcelas.stream().filter(ParcelaPrevia::pagaNaVenda).map(ParcelaPrevia::valor)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);

		return new Previa(form.clienteId(), cliente, hoje, itens, valorProdutos, acrescimo, total, parcelas, pagoAgora,
				total.subtract(pagoAgora));
	}

	private static void validarPagamentoNaHora(VendaForm form) {
		boolean temEntrada = form.forma() == FormaVenda.PARCELADO && form.entrada() != null && form.entrada().signum() > 0;
		if ((form.forma() == FormaVenda.A_VISTA || temEntrada) && form.formaPagamento() == null) {
			throw new RegraNegocioException(temEntrada ? "Escolha a forma de pagamento da entrada."
					: "Escolha a forma de pagamento.");
		}
		if (form.forma() == FormaVenda.PARCELADO && form.numeroParcelas() == null) {
			throw new RegraNegocioException("Informe em quantas parcelas.");
		}
	}

	/** Etiqueta ou modelo -> produto (e a peça, quando é etiqueta). */
	private List<Pedido> interpretar(List<String> codigos) {
		List<Pedido> pedidos = new ArrayList<>();
		for (String bruto : codigos) {
			String codigo = bruto == null ? "" : bruto.trim().toUpperCase().replaceAll("\\s+", "");
			if (codigo.isEmpty()) {
				continue;
			}
			Matcher etiqueta = ETIQUETA.matcher(codigo);
			if (etiqueta.matches()) {
				// "C0001-2" e "C0001-02" são a mesma etiqueta
				String numero = String.valueOf(Integer.parseInt(etiqueta.group(2)));
				String normalizada = etiqueta.group(1) + "-" + (numero.length() < 2 ? "0" + numero : numero);
				long peca = vendas.pecaPorEtiqueta(normalizada)
						.orElseThrow(() -> new RegraNegocioException("Etiqueta " + codigo + " não encontrada."));
				pedidos.add(new Pedido(codigo, peca, vendas.produtoDaPeca(peca).orElseThrow()));
			} else if (MODELO.matcher(codigo).matches()) {
				long produto = vendas.produtoPorCodigo(codigo)
						.orElseThrow(() -> new RegraNegocioException("Modelo " + codigo + " não encontrado."));
				pedidos.add(new Pedido(codigo, null, produto));
			} else {
				throw new RegraNegocioException("Código " + codigo + " inválido. Use a etiqueta (C0001-02) ou o modelo (C0001).");
			}
		}
		if (pedidos.isEmpty()) {
			throw new RegraNegocioException("Inclua pelo menos uma peça.");
		}
		return pedidos;
	}

	/** Etiquetas primeiro (peça certa), depois os modelos pegam a próxima disponível (FIFO). */
	private List<PecaParaVenda> escolherPecas(List<Pedido> pedidos) {
		Set<Long> escolhidas = new LinkedHashSet<>();
		Long[] porPosicao = new Long[pedidos.size()];
		for (int i = 0; i < pedidos.size(); i++) {
			Pedido p = pedidos.get(i);
			if (p.pecaId() != null) {
				if (!escolhidas.add(p.pecaId())) {
					throw new RegraNegocioException("A peça " + p.digitado() + " foi incluída mais de uma vez.");
				}
				porPosicao[i] = p.pecaId();
			}
		}
		for (int i = 0; i < pedidos.size(); i++) {
			Pedido p = pedidos.get(i);
			if (p.pecaId() == null) {
				Optional<Long> peca = vendas.primeiraPecaDisponivel(p.produtoId(), escolhidas);
				long id = peca.orElseThrow(() -> new RegraNegocioException(
						"Não há peça disponível do modelo " + p.digitado() + "."));
				escolhidas.add(id);
				porPosicao[i] = id;
			}
		}
		List<PecaParaVenda> pecas = new ArrayList<>();
		for (Long id : porPosicao) {
			PecaParaVenda peca = vendas.peca(id);
			if (!"DISPONIVEL".equals(peca.situacao())) {
				throw new RegraNegocioException("A peça " + peca.etiqueta() + " não está disponível ("
						+ situacaoLegivel(peca.situacao()) + ").");
			}
			if (!"ATIVO".equals(peca.produtoStatus())) {
				throw new RegraNegocioException("O modelo da peça " + peca.etiqueta() + " está inativo.");
			}
			pecas.add(peca);
		}
		return pecas;
	}

	private static String situacaoLegivel(String situacao) {
		return switch (situacao) {
			case "VENDIDA" -> "já foi vendida";
			case "PERDIDA" -> "marcada como perdida";
			case "DEVOLVIDA" -> "devolvida ao fornecedor";
			default -> "sem entrada no estoque";
		};
	}

}
