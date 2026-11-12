package br.com.itsjoias.service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.itsjoias.dto.ImportacaoDTO.ArquivoInfo;
import br.com.itsjoias.dto.ImportacaoDTO.Aviso;
import br.com.itsjoias.dto.ImportacaoDTO.DadosMaleta;
import br.com.itsjoias.dto.ImportacaoDTO.DecisaoPreco;
import br.com.itsjoias.dto.ImportacaoDTO.DocumentoInfo;
import br.com.itsjoias.dto.ImportacaoDTO.HistoricoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.ImportacaoAnterior;
import br.com.itsjoias.dto.ImportacaoDTO.LinhaConferida;
import br.com.itsjoias.dto.ImportacaoDTO.LinhaImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.NivelAviso;
import br.com.itsjoias.dto.ImportacaoDTO.Opcao;
import br.com.itsjoias.dto.ImportacaoDTO.PedidoImportacao;
import br.com.itsjoias.dto.ImportacaoDTO.Previa;
import br.com.itsjoias.dto.ImportacaoDTO.ProdutoExistente;
import br.com.itsjoias.dto.ImportacaoDTO.Resultado;
import br.com.itsjoias.dto.ImportacaoDTO.Resumo;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.importacao.CodigoBarras;
import br.com.itsjoias.importacao.DocumentoLido;
import br.com.itsjoias.importacao.InterpretadorDocumento;
import br.com.itsjoias.importacao.LeitorPdf;
import br.com.itsjoias.importacao.LeitorPdf.Leitura;
import br.com.itsjoias.importacao.MescladorLeituras;
import br.com.itsjoias.repository.ImportacaoRepository;
import br.com.itsjoias.repository.ImportacaoRepository.Fornecedor;
import br.com.itsjoias.repository.ImportacaoRepository.PalavraChave;
import br.com.itsjoias.seguranca.UsuarioAtual;

/**
 * Importação de maleta por PDF, em três passos:
 * 1. ler: abre o PDF, entende as linhas e devolve a PRÉVIA (nada é gravado);
 * 2. validar: a cada correção feita na tela, confere tudo de novo;
 * 3. confirmar: confere uma última vez e grava TUDO numa transação (ou nada, se algo falhar).
 */
@Service
public class ImportacaoService {

	public static final long TAMANHO_MAXIMO = 15L * 1024 * 1024;
	private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Pattern FORMATO_CNPJ = Pattern.compile("^\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}$");
	private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999.99");
	private static final Set<String> ABERTURAS_EXTRAS = Set.of("BRINCO", "ANEL", "COLAR", "PULSEIRA", "TORNOZELEIRA",
			"PINGENTE", "CONJUNTO", "KIT", "GARGANTILHA", "CHOKER", "ARGOLA", "CORRENTE", "MALETA", "MOSTRUARIO");

	private final LeitorPdf leitor;
	private final ImportacaoRepository repo;
	private final CalendarioCobranca calendario;
	private final UsuarioAtual usuarioAtual;

	public ImportacaoService(LeitorPdf leitor, ImportacaoRepository repo, CalendarioCobranca calendario,
			UsuarioAtual usuarioAtual) {
		this.leitor = leitor;
		this.repo = repo;
		this.calendario = calendario;
		this.usuarioAtual = usuarioAtual;
	}

	// =======================================================================================
	// 1. LER
	// =======================================================================================

	@Transactional(readOnly = true)
	public Previa ler(String nomeArquivo, byte[] conteudo) {
		if (conteudo == null || conteudo.length == 0) {
			throw new RegraNegocioException("O arquivo está vazio.");
		}
		if (conteudo.length > TAMANHO_MAXIMO) {
			throw new RegraNegocioException("O arquivo é grande demais (máximo 15 MB).");
		}
		String nome = nomeArquivo == null || nomeArquivo.isBlank() ? "maleta.pdf" : nomeArquivo.trim();
		if (nome.length() > 255) {
			nome = nome.substring(nome.length() - 255);
		}
		Leitura leitura = leitor.ler(conteudo);
		List<PalavraChave> palavras = repo.palavrasChave();
		InterpretadorDocumento interpretador = new InterpretadorDocumento(aberturas(palavras));

		DocumentoLido a = interpretador.interpretar(leitura.principal());
		List<DocumentoLido.Linha> linhas = a.linhas();
		DocumentoLido.Cabecalho cab = a.cabecalho();
		if (leitura.metodo() == LeitorPdf.Metodo.OCR) {
			DocumentoLido b = interpretador.interpretar(leitura.secundaria());
			linhas = MescladorLeituras.mesclar(a.linhas(), b.linhas());
			cab = completar(cab, b.cabecalho());
		}
		if (linhas.isEmpty()) {
			throw new RegraNegocioException("Não foi encontrada nenhuma peça no PDF. Confira se é o termo/lista de "
					+ "peças da maleta; se for uma foto, veja se está nítida e com o papel reto.");
		}

		ArquivoInfo arquivo = new ArquivoInfo(nome, sha256(conteudo), conteudo.length, leitura.metodo().name(),
				leitura.paginas());
		DocumentoInfo documento = new DocumentoInfo(cab.fornecedorNome(), cab.cnpj(), cab.pedido(), cab.dataCriacao(),
				cab.dataAcerto(), cab.valorTotalItens());

		List<LinhaImportacao> itens = new ArrayList<>();
		for (int i = 0; i < linhas.size(); i++) {
			DocumentoLido.Linha l = linhas.get(i);
			String descricao = l.descricao() == null ? null : cortar(l.descricao(), 120);
			itens.add(new LinhaImportacao(i + 1, !mostruario(descricao), l.codigo(), descricao,
					sugerirCategoria(descricao, palavras), l.quantidade(), l.valorUnitario(), false, null,
					cortar(l.textoOriginal(), 300), l.valorTotal(), l.quantidadeDeduzida()));
		}
		return validar(new PedidoImportacao(arquivo, documento, sugerirMaleta(documento), itens, false));
	}

	// =======================================================================================
	// 2. VALIDAR
	// =======================================================================================

	@Transactional(readOnly = true)
	public Previa validar(PedidoImportacao pedido) {
		if (pedido == null || pedido.arquivo() == null || pedido.linhas() == null || pedido.maleta() == null) {
			throw new RegraNegocioException("Dados da importação incompletos. Leia o PDF de novo.");
		}
		List<Fornecedor> fornecedores = repo.fornecedores();
		List<Aviso> avisosMaleta = new ArrayList<>();
		Long fornecedorId = resolverFornecedor(pedido.maleta(), fornecedores, avisosMaleta);
		validarMaleta(pedido.maleta(), avisosMaleta);

		List<LinhaImportacao> linhas = pedido.linhas();
		Set<String> codigos = linhas.stream().filter(LinhaImportacao::importar).map(l -> limparCodigo(l.codigo()))
				.filter(c -> c != null && c.matches("\\d{6,14}")).collect(Collectors.toSet());
		Map<String, ProdutoExistente> existentes = fornecedorId == null ? Map.of()
				: repo.produtosPorCodigoFornecedor(fornecedorId, codigos);
		Integer tamanhoTipico = tamanhoTipicoDosCodigos(linhas);
		Map<String, List<Integer>> repetidos = new HashMap<>();
		for (LinhaImportacao l : linhas) {
			String c = limparCodigo(l.codigo());
			if (l.importar() && c != null) {
				repetidos.computeIfAbsent(c, k -> new ArrayList<>()).add(l.indice());
			}
		}
		Set<Long> categoriasValidas = repo.categorias().stream().map(Opcao::id).collect(Collectors.toSet());

		List<LinhaConferida> conferidas = new ArrayList<>();
		for (LinhaImportacao l : linhas) {
			conferidas.add(conferirLinha(l, existentes, repetidos, tamanhoTipico, categoriasValidas));
		}

		List<Aviso> avisosGerais = new ArrayList<>();
		BigDecimal somaDocumento = linhas.stream()
				.filter(l -> l.quantidade() != null && l.valorUnitario() != null)
				.map(l -> l.valorUnitario().multiply(BigDecimal.valueOf(l.quantidade())))
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
		if (pedido.documento() != null && pedido.documento().valorTotalDocumento() != null
				&& somaDocumento.compareTo(pedido.documento().valorTotalDocumento()) != 0) {
			avisosGerais.add(new Aviso(NivelAviso.ALERTA, "A soma das linhas (" + reais(somaDocumento)
					+ ") não bate com o total do documento (" + reais(pedido.documento().valorTotalDocumento())
					+ "). Pode ter faltado alguma linha ou algum valor foi lido errado: confira com o papel."));
		}
		if ("OCR".equals(pedido.arquivo().metodo())) {
			avisosGerais.add(new Aviso(NivelAviso.INFO, "Este PDF é uma foto/escaneado: o texto foi reconhecido "
					+ "automaticamente e pode ter erros. Confira principalmente as linhas destacadas."));
		}
		String hash = pedido.arquivo().hash();
		List<ImportacaoAnterior> anteriores = List.of();
		if (hash == null || !hash.matches("^[0-9a-f]{64}$")) {
			avisosGerais.add(new Aviso(NivelAviso.ERRO, "Arquivo não identificado. Leia o PDF de novo."));
		} else {
			anteriores = repo.importacoesDoArquivo(hash);
			for (ImportacaoAnterior ant : anteriores) {
				avisosGerais.add(new Aviso(NivelAviso.ALERTA, "Este mesmo arquivo já foi importado em "
						+ ant.data().atZoneSameInstant(CalendarioCobranca.FUSO_SAO_PAULO).format(BR) + " por "
						+ ant.usuario() + " (" + ant.maleta() + ", " + ant.pecas() + " peças). Importar de novo "
						+ "cadastra as peças outra vez."));
			}
		}

		Resumo resumo = resumir(conferidas);
		boolean semErros = conferidas.stream().noneMatch(LinhaConferida::temErro)
				&& avisosMaleta.stream().noneMatch(a -> a.nivel() == NivelAviso.ERRO)
				&& avisosGerais.stream().noneMatch(a -> a.nivel() == NivelAviso.ERRO);
		boolean podeConfirmar = semErros && resumo.aImportar() > 0;
		if (resumo.aImportar() == 0) {
			avisosGerais.add(new Aviso(NivelAviso.ERRO, "Nenhuma linha marcada para importar."));
		}

		DadosMaleta maleta = pedido.maleta();
		if (fornecedorId != null && maleta.fornecedorId() == null) {
			maleta = new DadosMaleta(fornecedorId, null, maleta.fornecedorCnpj(), maleta.codigo(), maleta.nome(),
					maleta.dataEntrada(), maleta.dataAcerto());
		}
		List<Opcao> opcoesFornecedor = fornecedores.stream().filter(Fornecedor::ativo)
				.map(f -> new Opcao(f.id(), f.nome())).toList();
		return new Previa(pedido.arquivo(), pedido.documento(), maleta, avisosMaleta, conferidas, avisosGerais,
				anteriores, resumo, podeConfirmar, repo.categorias(), opcoesFornecedor);
	}

	private LinhaConferida conferirLinha(LinhaImportacao l, Map<String, ProdutoExistente> existentes,
			Map<String, List<Integer>> repetidos, Integer tamanhoTipico, Set<Long> categoriasValidas) {
		List<Aviso> avisos = new ArrayList<>();
		String codigo = limparCodigo(l.codigo());
		BigDecimal total = l.quantidade() != null && l.valorUnitario() != null
				? l.valorUnitario().multiply(BigDecimal.valueOf(l.quantidade())).setScale(2) : null;

		if (!l.importar()) {
			if (mostruario(l.descricao())) {
				avisos.add(new Aviso(NivelAviso.INFO, "Mostruário/maleta física: não entra como peça."));
			} else {
				avisos.add(new Aviso(NivelAviso.INFO, "Esta linha não será importada."));
			}
			return new LinhaConferida(l, total, avisos, null, false);
		}

		// código do fornecedor
		ProdutoExistente existente = null;
		if (codigo == null) {
			erro(avisos, "Informe o código do fornecedor (o código de barras da linha).");
		} else if (!codigo.matches("\\d{6,14}")) {
			erro(avisos, "O código deve ter só números (de 6 a 14 dígitos).");
		} else {
			CodigoBarras.Resultado conferencia = CodigoBarras.conferir(codigo);
			if (conferencia == CodigoBarras.Resultado.INVALIDO && !l.codigoConferido()) {
				erro(avisos, "Este código não passou na conferência automática (dígito verificador): provavelmente "
						+ "foi lido errado. Confira no papel; se estiver certo, marque \"conferido\".");
			} else if (conferencia == CodigoBarras.Resultado.SEM_VERIFICACAO && tamanhoTipico != null
					&& codigo.length() != tamanhoTipico && !l.codigoConferido()) {
				erro(avisos, "Os códigos deste documento têm " + tamanhoTipico + " dígitos e este tem "
						+ codigo.length() + ". Confira no papel; se estiver certo, marque \"conferido\".");
			}
			if (repetidos.getOrDefault(codigo, List.of()).size() > 1) {
				erro(avisos, "Código repetido no arquivo (linhas " + repetidos.get(codigo).stream().map(String::valueOf)
						.collect(Collectors.joining(", ")) + "). Some as quantidades numa linha só e desmarque as outras.");
			}
			existente = existentes.get(codigo);
		}

		// descrição
		String descricao = l.descricao() == null ? "" : l.descricao().trim();
		if (existente == null) {
			if (descricao.length() < 3) {
				erro(avisos, "Informe a descrição da peça.");
			} else if (descricao.length() > 120) {
				erro(avisos, "A descrição pode ter no máximo 120 caracteres.");
			}
		}

		// categoria
		if (existente == null) {
			if (l.categoriaId() == null || !categoriasValidas.contains(l.categoriaId())) {
				erro(avisos, "Escolha a categoria (o código interno da peça depende dela e não muda depois).");
			}
		}

		// quantidade e valor
		if (l.quantidade() == null || l.quantidade() < 1 || l.quantidade() > 999) {
			erro(avisos, "Informe a quantidade (de 1 a 999).");
		} else if (l.quantidadeDeduzida()) {
			avisos.add(new Aviso(NivelAviso.ALERTA, "A quantidade não estava legível; foi calculada pelo total da linha."));
		}
		if (l.valorUnitario() == null || l.valorUnitario().signum() <= 0) {
			erro(avisos, "Informe o valor unitário da peça.");
		} else if (l.valorUnitario().scale() > 2 && l.valorUnitario().stripTrailingZeros().scale() > 2) {
			erro(avisos, "Use no máximo 2 casas decimais no valor.");
		} else if (l.valorUnitario().compareTo(VALOR_MAXIMO) > 0) {
			erro(avisos, "Valor unitário alto demais. Confira.");
		}
		if (total != null && l.valorTotalLido() != null && total.compareTo(l.valorTotalLido()) != 0) {
			avisos.add(new Aviso(NivelAviso.ALERTA, "No documento, o total desta linha é " + reais(l.valorTotalLido())
					+ ", mas quantidade × valor dá " + reais(total) + ". Confira."));
		}

		// modelo já cadastrado
		if (existente != null) {
			avisos.add(new Aviso(NivelAviso.INFO, "Modelo já cadastrado: " + existente.codigo() + " – " + existente.nome()
					+ " (" + existente.categoria() + "). As peças novas entram nesse modelo."));
			if (l.valorUnitario() != null && existente.precoVenda().compareTo(l.valorUnitario()) != 0) {
				if (l.decisaoPreco() == null) {
					erro(avisos, "O preço no documento (" + reais(l.valorUnitario()) + ") é diferente do cadastrado ("
							+ reais(existente.precoVenda()) + "). Escolha manter o preço atual ou atualizar.");
				} else if (l.decisaoPreco() == DecisaoPreco.ATUALIZAR) {
					avisos.add(new Aviso(NivelAviso.ALERTA, "O preço do modelo vai mudar de " + reais(existente.precoVenda())
							+ " para " + reais(l.valorUnitario()) + "."));
				}
			}
		} else if (descricao.length() >= 3) {
			avisos.add(new Aviso(NivelAviso.INFO, "Modelo novo: será criado com um código interno próprio."));
		}

		boolean temErro = avisos.stream().anyMatch(a -> a.nivel() == NivelAviso.ERRO);
		return new LinhaConferida(l, total, avisos, existente, temErro);
	}

	// =======================================================================================
	// 3. CONFIRMAR
	// =======================================================================================

	@Transactional
	public Resultado confirmar(PedidoImportacao pedido) {
		Previa previa = validar(pedido);
		if (!previa.podeConfirmar()) {
			String primeiro = previa.avisosMaleta().stream().filter(a -> a.nivel() == NivelAviso.ERRO).map(Aviso::mensagem)
					.findFirst().orElse(null);
			long linhasComErro = previa.linhas().stream().filter(LinhaConferida::temErro).count();
			throw new RegraNegocioException(primeiro != null ? primeiro
					: linhasComErro > 0 ? "Há " + linhasComErro + " linha(s) com erro. Corrija antes de confirmar."
							: previa.avisosGerais().stream().filter(a -> a.nivel() == NivelAviso.ERRO).map(Aviso::mensagem)
									.findFirst().orElse("A importação não pode ser confirmada."));
		}
		if (!previa.importacoesDoMesmoArquivo().isEmpty() && !pedido.confirmarReimportacao()) {
			throw new RegraNegocioException("Este arquivo já foi importado antes. Para importar de novo, marque a "
					+ "confirmação de reimportação.");
		}
		long usuario = usuarioAtual.obter().id();
		DadosMaleta m = previa.maleta();

		long fornecedorId;
		if (m.fornecedorId() != null) {
			fornecedorId = m.fornecedorId();
			String cnpj = cnpjValido(m.fornecedorCnpj());
			if (cnpj != null && repo.fornecedores().stream().noneMatch(f -> cnpj.equals(f.cnpj()))) {
				repo.completarCnpj(fornecedorId, cnpj);
			}
		} else {
			fornecedorId = repo.inserirFornecedor(m.fornecedorNovoNome().trim(), cnpjValido(m.fornecedorCnpj()));
		}
		long maletaId = repo.inserirMaleta(m.codigo().trim(), m.nome().trim(), fornecedorId, m.dataEntrada(),
				m.dataAcerto());

		// modelos: existentes (talvez com preço novo) ou novos; peças agrupadas por modelo
		record Grupo(long produtoId, String codigo, String descricao, BigDecimal custo, int quantidade) {
		}
		Map<Long, Grupo> grupos = new LinkedHashMap<>();
		int modelosNovos = 0;
		int ignoradas = 0;
		BigDecimal valorTotal = BigDecimal.ZERO;
		for (LinhaConferida c : previa.linhas()) {
			LinhaImportacao l = c.linha();
			if (!l.importar()) {
				ignoradas++;
				continue;
			}
			String codigo = limparCodigo(l.codigo());
			long produtoId;
			if (c.produtoExistente() != null) {
				produtoId = c.produtoExistente().id();
				if (l.decisaoPreco() == DecisaoPreco.ATUALIZAR) {
					repo.atualizarPreco(produtoId, l.valorUnitario());
				}
			} else {
				produtoId = repo.inserirProduto(l.descricao().trim(), l.categoriaId(), l.valorUnitario(), fornecedorId, codigo);
				modelosNovos++;
			}
			Grupo anterior = grupos.get(produtoId);
			if (anterior != null && anterior.custo().compareTo(l.valorUnitario()) != 0) {
				throw new RegraNegocioException("Os códigos " + anterior.codigo() + " e " + codigo + " são do mesmo modelo "
						+ "mas com valores diferentes. Confira as duas linhas.");
			}
			grupos.put(produtoId, new Grupo(produtoId, codigo, l.descricao(), l.valorUnitario(),
					(anterior == null ? 0 : anterior.quantidade()) + l.quantidade()));
			valorTotal = valorTotal.add(c.valorTotal());
		}
		repo.travarProdutos(grupos.keySet().stream().sorted().toList());
		int pecas = 0;
		for (Grupo g : grupos.values()) {
			repo.inserirItemComPecas(maletaId, g.produtoId(), g.quantidade(), g.custo(), g.codigo(),
					cortar(g.descricao(), 500), usuario);
			pecas += g.quantidade();
		}
		ArquivoInfo arq = previa.arquivo();
		long importacaoId = repo.inserirHistorico(maletaId, fornecedorId, usuario, arq.nome(), arq.hash(), arq.tamanho(),
				arq.metodo(), previa.documento() == null ? null : cortar(previa.documento().pedido(), 40),
				previa.linhas().size(), ignoradas, modelosNovos, pecas, valorTotal.setScale(2),
				!previa.importacoesDoMesmoArquivo().isEmpty());
		// As triggers conferem no COMMIT: cada item da maleta tem exatamente "quantidade" peças com ENTRADA.
		return new Resultado(importacaoId, maletaId, m.nome().trim(), pecas, modelosNovos, ignoradas,
				valorTotal.setScale(2), repo.etiquetasDaMaleta(maletaId));
	}

	@Transactional(readOnly = true)
	public List<HistoricoImportacao> historico(int limite) {
		return repo.historico(Math.clamp(limite, 1, 200));
	}

	// =======================================================================================
	// apoio
	// =======================================================================================

	private Long resolverFornecedor(DadosMaleta m, List<Fornecedor> fornecedores, List<Aviso> avisos) {
		if (m.fornecedorId() != null) {
			Optional<Fornecedor> f = fornecedores.stream().filter(x -> x.id() == m.fornecedorId()).findFirst();
			if (f.isEmpty() || !f.get().ativo()) {
				avisos.add(new Aviso(NivelAviso.ERRO, "Fornecedor não encontrado. Escolha outro."));
				return null;
			}
			return m.fornecedorId();
		}
		String nome = m.fornecedorNovoNome() == null ? "" : m.fornecedorNovoNome().trim();
		if (nome.isEmpty()) {
			avisos.add(new Aviso(NivelAviso.ERRO, "Escolha o fornecedor da maleta (ou informe o nome de um novo)."));
			return null;
		}
		if (nome.length() > 120) {
			avisos.add(new Aviso(NivelAviso.ERRO, "O nome do fornecedor pode ter no máximo 120 caracteres."));
			return null;
		}
		Optional<Fornecedor> mesmoNome = fornecedores.stream().filter(f -> f.nome().equalsIgnoreCase(nome)).findFirst();
		if (mesmoNome.isPresent()) {
			avisos.add(new Aviso(NivelAviso.INFO, "Fornecedor já cadastrado: " + mesmoNome.get().nome() + "."));
			return mesmoNome.get().id();
		}
		if (m.fornecedorCnpj() != null && !m.fornecedorCnpj().isBlank()) {
			String cnpj = cnpjValido(m.fornecedorCnpj());
			if (cnpj == null) {
				avisos.add(new Aviso(NivelAviso.ERRO, "CNPJ do fornecedor inválido. Use 00.000.000/0000-00 ou deixe em branco."));
			} else {
				Optional<Fornecedor> mesmoCnpj = fornecedores.stream().filter(f -> cnpj.equals(f.cnpj())).findFirst();
				if (mesmoCnpj.isPresent()) {
					avisos.add(new Aviso(NivelAviso.INFO, "Este CNPJ é do fornecedor " + mesmoCnpj.get().nome() + "."));
					return mesmoCnpj.get().id();
				}
			}
		}
		avisos.add(new Aviso(NivelAviso.INFO, "Fornecedor novo: \"" + nome + "\" será cadastrado."));
		return null;
	}

	private void validarMaleta(DadosMaleta m, List<Aviso> avisos) {
		String codigo = m.codigo() == null ? "" : m.codigo().trim();
		if (codigo.isEmpty()) {
			avisos.add(new Aviso(NivelAviso.ERRO, "Informe um código para a maleta."));
		} else if (codigo.length() > 20) {
			avisos.add(new Aviso(NivelAviso.ERRO, "O código da maleta pode ter no máximo 20 caracteres."));
		} else if (repo.maletaExiste(codigo)) {
			avisos.add(new Aviso(NivelAviso.ERRO, "Já existe uma maleta com o código " + codigo
					+ " (este pedido pode já ter sido importado). Use outro código se for outra maleta."));
		}
		String nome = m.nome() == null ? "" : m.nome().trim();
		if (nome.isEmpty()) {
			avisos.add(new Aviso(NivelAviso.ERRO, "Informe um nome para a maleta."));
		} else if (nome.length() > 120) {
			avisos.add(new Aviso(NivelAviso.ERRO, "O nome da maleta pode ter no máximo 120 caracteres."));
		}
		if (m.dataEntrada() == null) {
			avisos.add(new Aviso(NivelAviso.ERRO, "Informe a data de entrada da maleta."));
		} else if (m.dataEntrada().isAfter(calendario.hoje())) {
			avisos.add(new Aviso(NivelAviso.ALERTA, "A data de entrada está no futuro. Confira."));
		}
		if (m.dataAcerto() != null && m.dataEntrada() != null && m.dataAcerto().isBefore(m.dataEntrada())) {
			avisos.add(new Aviso(NivelAviso.ALERTA, "A data de acerto é anterior à data de entrada. Confira."));
		}
	}

	private DadosMaleta sugerirMaleta(DocumentoInfo doc) {
		List<Fornecedor> fornecedores = repo.fornecedores();
		Long fornecedorId = null;
		String novoNome = null;
		if (doc.cnpj() != null) {
			fornecedorId = fornecedores.stream().filter(f -> doc.cnpj().equals(f.cnpj())).map(Fornecedor::id).findFirst()
					.orElse(null);
		}
		if (fornecedorId == null && doc.fornecedorNome() != null) {
			String alvo = nomeComparavel(doc.fornecedorNome());
			fornecedorId = fornecedores.stream().filter(f -> nomeComparavel(f.nome()).equals(alvo)).map(Fornecedor::id)
					.findFirst().orElse(null);
			if (fornecedorId == null) {
				novoNome = capitalizar(doc.fornecedorNome());
			}
		}
		LocalDate entrada = doc.dataCriacao() != null ? doc.dataCriacao() : calendario.hoje();
		String codigo = doc.pedido() != null ? cortar("PED" + doc.pedido(), 20)
				: "IMP" + calendario.hoje().format(DateTimeFormatter.BASIC_ISO_DATE);
		String nome = doc.dataAcerto() != null ? "Maleta acerto " + doc.dataAcerto().format(BR)
				: "Maleta de " + entrada.format(BR);
		return new DadosMaleta(fornecedorId, novoNome, fornecedorId == null ? doc.cnpj() : null, codigo, nome, entrada,
				doc.dataAcerto());
	}

	/** Categoria pela descrição, usando as palavras-chave do banco (ex.: "gargantilha" -> Colar). */
	static Long sugerirCategoria(String descricao, List<PalavraChave> palavras) {
		if (descricao == null) {
			return null;
		}
		String texto = " " + InterpretadorDocumento.normalizar(descricao).toLowerCase().replaceAll("[^a-z0-9]+", " ") + " ";
		PalavraChave melhor = null;
		int melhorPosicao = Integer.MAX_VALUE;
		for (PalavraChave p : palavras) {
			int pos = texto.indexOf(" " + p.termo() + " ");
			if (pos < 0) {
				continue;
			}
			if (melhor == null || p.prioridade() > melhor.prioridade()
					|| (p.prioridade() == melhor.prioridade() && pos < melhorPosicao)) {
				melhor = p;
				melhorPosicao = pos;
			}
		}
		return melhor == null ? null : melhor.categoriaId();
	}

	private static Set<String> aberturas(List<PalavraChave> palavras) {
		Set<String> s = new LinkedHashSet<>(ABERTURAS_EXTRAS);
		palavras.stream().map(p -> p.termo().toUpperCase()).filter(t -> !t.contains(" ")).forEach(s::add);
		return s;
	}

	static boolean mostruario(String descricao) {
		if (descricao == null) {
			return false;
		}
		String n = InterpretadorDocumento.normalizar(descricao);
		return n.contains("MALETA") || n.contains("MOSTRUARIO") || n.matches("^MOS\\s?\\d.*");
	}

	private static Integer tamanhoTipicoDosCodigos(List<LinhaImportacao> linhas) {
		Map<Integer, Long> contagem = linhas.stream().map(l -> limparCodigo(l.codigo()))
				.filter(c -> c != null && c.matches("\\d+") && CodigoBarras.conferir(c) == CodigoBarras.Resultado.VALIDO)
				.collect(Collectors.groupingBy(String::length, Collectors.counting()));
		return contagem.entrySet().stream().max(Map.Entry.comparingByValue()).filter(e -> e.getValue() >= 3)
				.map(Map.Entry::getKey).orElse(null);
	}

	private static String limparCodigo(String codigo) {
		if (codigo == null) {
			return null;
		}
		String c = codigo.replaceAll("\\s+", "").toUpperCase();
		return c.isEmpty() ? null : c;
	}

	private static DocumentoLido.Cabecalho completar(DocumentoLido.Cabecalho a, DocumentoLido.Cabecalho b) {
		return new DocumentoLido.Cabecalho(primeiro(a.fornecedorNome(), b.fornecedorNome()), primeiro(a.cnpj(), b.cnpj()),
				primeiro(a.pedido(), b.pedido()), primeiro(a.dataCriacao(), b.dataCriacao()),
				primeiro(a.dataAcerto(), b.dataAcerto()), primeiro(a.valorTotalItens(), b.valorTotalItens()));
	}

	private static <T> T primeiro(T a, T b) {
		return a != null ? a : b;
	}

	private static Resumo resumir(List<LinhaConferida> linhas) {
		int aImportar = 0, ignoradas = 0, comErro = 0, pecas = 0, novos = 0, existentes = 0;
		BigDecimal valor = BigDecimal.ZERO;
		Set<Long> modelos = new HashSet<>();
		for (LinhaConferida c : linhas) {
			if (!c.linha().importar()) {
				ignoradas++;
				continue;
			}
			aImportar++;
			if (c.temErro()) {
				comErro++;
			}
			if (c.linha().quantidade() != null && c.linha().quantidade() > 0) {
				pecas += c.linha().quantidade();
			}
			if (c.valorTotal() != null) {
				valor = valor.add(c.valorTotal());
			}
			if (c.produtoExistente() != null) {
				if (modelos.add(c.produtoExistente().id())) {
					existentes++;
				}
			} else {
				novos++;
			}
		}
		return new Resumo(linhas.size(), aImportar, ignoradas, comErro, pecas, valor.setScale(2), novos, existentes);
	}

	private static void erro(List<Aviso> avisos, String mensagem) {
		avisos.add(new Aviso(NivelAviso.ERRO, mensagem));
	}

	static String cnpjValido(String cnpj) {
		if (cnpj == null) {
			return null;
		}
		String c = cnpj.trim();
		if (!FORMATO_CNPJ.matcher(c).matches()) {
			return null;
		}
		String d = c.replaceAll("\\D", "");
		if (d.chars().distinct().count() == 1) {
			return null;
		}
		int[] p1 = { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };
		int[] p2 = { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };
		return digito(d, p1) == d.charAt(12) - '0' && digito(d, p2) == d.charAt(13) - '0' ? c : null;
	}

	private static int digito(String d, int[] pesos) {
		int soma = 0;
		for (int i = 0; i < pesos.length; i++) {
			soma += (d.charAt(i) - '0') * pesos[i];
		}
		int r = soma % 11;
		return r < 2 ? 0 : 11 - r;
	}

	private static String nomeComparavel(String nome) {
		return InterpretadorDocumento.normalizar(nome).replaceAll("\\b(LTDA|EIRELI|ME|EPP|S/?A)\\b", "")
				.replaceAll("[^A-Z0-9]", "");
	}

	private static String capitalizar(String nome) {
		String[] partes = nome.trim().toLowerCase().split("\\s+");
		StringBuilder sb = new StringBuilder();
		for (String p : partes) {
			if (p.isEmpty()) {
				continue;
			}
			sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		}
		return sb.toString();
	}

	private static String cortar(String texto, int max) {
		if (texto == null) {
			return null;
		}
		String t = texto.trim();
		return t.length() <= max ? t : t.substring(0, max).trim();
	}

	private static String reais(BigDecimal valor) {
		return "R$ " + String.format(java.util.Locale.of("pt", "BR"), "%,.2f", valor);
	}

	static String sha256(byte[] conteudo) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
