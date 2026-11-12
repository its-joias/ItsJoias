// Nova venda.
// O front só junta o que a vendedora escolheu. Peças, total e parcelas são calculados pelo
// back-end (POST /api/vendas/previa) e gravados por ele (POST /api/vendas).
(function () {
    const { api, formato, criar, perguntar } = window.ItsJoias;
    const { reaisDigitados } = window.ItsJoiasMascaras;
    const el = (id) => document.getElementById(id);

    const NOMES_FORMA = { PIX: "PIX", DINHEIRO: "Dinheiro", CARTAO: "Cartão", TRANSFERENCIA: "Transferência", OUTRO: "Outro" };
    const MAX_PARCELAS = 24;

    const selCliente = el("cliente");
    const inpCodigo = el("codigo");
    const sugestoes = el("sugestoes");
    const listaPecas = el("lista-pecas");
    const semPecas = el("sem-pecas");
    const selParcelas = el("parcelas");
    const inpEntrada = el("entrada");
    const selFormaPagamento = el("forma-pagamento");
    const chkAcrescimo = el("acrescimo");
    const resumoErro = el("resumo-erro");
    const resumoVazio = el("resumo-vazio");
    const resumoConteudo = el("resumo-conteudo");
    const botaoConfirmar = el("botao-confirmar");

    /** Códigos digitados, na ordem. */
    let codigos = [];
    /** Última prévia válida (é ela que aparece na lista de peças e no resumo). */
    let previa = null;

    for (let n = 1; n <= MAX_PARCELAS; n++) {
        selParcelas.append(new Option(n === 1 ? "1 parcela" : `${n} parcelas`, String(n)));
    }
    selParcelas.value = "3";

    function forma() {
        return document.querySelector('input[name="forma"]:checked').value;
    }

    function atualizarCamposPagamento() {
        const parcelado = forma() === "PARCELADO";
        el("campo-parcelas").hidden = !parcelado;
        el("campo-entrada").hidden = !parcelado;
        const entrada = reaisDigitados(inpEntrada.value);
        const temEntrada = parcelado && entrada !== null && !Number.isNaN(entrada) && Number(entrada) > 0;
        el("campo-forma-pagamento").hidden = parcelado && !temEntrada;
        el("rotulo-forma-pagamento").textContent = parcelado ? "Forma de pagamento da entrada" : "Forma de pagamento";
    }

    // ---- montar o pedido e pedir a prévia ----------------------------------------------

    function pedido() {
        const parcelado = forma() === "PARCELADO";
        const entrada = parcelado ? reaisDigitados(inpEntrada.value) : null;
        return {
            clienteId: selCliente.value ? Number(selCliente.value) : null,
            codigos,
            acrescimo10: chkAcrescimo.checked,
            forma: forma(),
            numeroParcelas: parcelado ? Number(selParcelas.value) : null,
            entrada: Number.isNaN(entrada) ? "invalida" : entrada,
            formaPagamento: el("campo-forma-pagamento").hidden ? null : selFormaPagamento.value,
        };
    }

    function erroNoResumo(texto) {
        resumoErro.textContent = texto;
        resumoErro.hidden = !texto;
    }

    let pedidoAtual = 0;
    let espera;
    let conferindo = false;

    function atualizarPrevia() {
        clearTimeout(espera);
        espera = setTimeout(pedirPrevia, 250);
    }

    async function pedirPrevia() {
        atualizarCamposPagamento();
        const dados = pedido();
        const meu = ++pedidoAtual;
        botaoConfirmar.disabled = true;
        if (!dados.clienteId || codigos.length === 0) {
            previa = null;
            desenharPecas();
            desenharResumo();
            erroNoResumo("");
            return;
        }
        if (dados.entrada === "invalida") {
            erroNoResumo("Valor da entrada inválido. Use, por exemplo, 50,00.");
            return;
        }
        conferindo = true;
        desenharPecas();
        try {
            const resposta = await api("/api/vendas/previa", { metodo: "POST", corpo: dados });
            if (meu !== pedidoAtual) return;
            previa = resposta;
            erroNoResumo("");
        } catch (erro) {
            if (meu !== pedidoAtual) return;
            erroNoResumo(erro.message);
            previa = null;
        }
        conferindo = false;
        desenharPecas();
        desenharResumo();
    }

    // ---- peças --------------------------------------------------------------------------

    function adicionar(codigo) {
        const c = (codigo || "").trim().toUpperCase();
        if (!c) return;
        codigos.push(c);
        inpCodigo.value = "";
        sugestoes.replaceChildren();
        desenharPecas();
        atualizarPrevia();
        inpCodigo.focus();
    }

    function remover(indice) {
        codigos.splice(indice, 1);
        desenharPecas();
        atualizarPrevia();
    }

    function desenharPecas() {
        semPecas.hidden = codigos.length > 0;
        const itens = previa && previa.itens.length === codigos.length ? previa.itens : null;
        listaPecas.replaceChildren(...codigos.map((codigo, i) => {
            const li = criar("li");
            const info = criar("div", "peca-info");
            const direita = criar("div", "peca-direita");
            if (itens) {
                const item = itens[i];
                info.append(criar("strong", null, `${item.etiqueta} · ${item.produto}`),
                    criar("span", "texto-suave", `${item.categoria} · ${item.maleta}`));
                direita.append(criar("span", "dinheiro", formato.reais(item.preco)));
            } else {
                info.append(criar("strong", null, codigo), criar("span", "texto-suave",
                    conferindo ? "conferindo…" : (selCliente.value ? "verifique este código" : "escolha a cliente para conferir")));
            }
            const botao = criar("button", "botao-secundario botao-pequeno", "Remover");
            botao.type = "button";
            botao.setAttribute("aria-label", `Remover ${codigo}`);
            botao.addEventListener("click", () => remover(i));
            direita.append(botao);
            li.append(info, direita);
            return li;
        }));
    }

    let esperaBusca;
    let buscaAtual = 0;

    inpCodigo.addEventListener("input", () => {
        clearTimeout(esperaBusca);
        const termo = inpCodigo.value.trim();
        if (termo.length < 2) {
            sugestoes.replaceChildren();
            return;
        }
        esperaBusca = setTimeout(async () => {
            const meu = ++buscaAtual;
            try {
                const pecas = await api("/api/pecas?limite=8&busca=" + encodeURIComponent(termo));
                if (meu !== buscaAtual) return;
                const usadas = new Set(previa ? previa.itens.map((i) => i.etiqueta) : codigos);
                sugestoes.replaceChildren(...pecas.filter((p) => !usadas.has(p.etiqueta)).map((p) => {
                    const li = criar("li");
                    const botao = criar("button");
                    botao.type = "button";
                    botao.append(criar("span", null, `${p.etiqueta} · ${p.produto} · ${p.maleta}`),
                        criar("span", "dinheiro", formato.reais(p.preco)));
                    botao.addEventListener("click", () => adicionar(p.etiqueta));
                    li.append(botao);
                    return li;
                }));
            } catch (_) {
                sugestoes.replaceChildren();
            }
        }, 300);
    });

    inpCodigo.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            adicionar(inpCodigo.value);
        }
    });
    el("botao-adicionar").addEventListener("click", () => adicionar(inpCodigo.value));

    // ---- resumo -------------------------------------------------------------------------

    function desenharResumo() {
        resumoVazio.hidden = Boolean(previa);
        resumoConteudo.hidden = !previa;
        botaoConfirmar.disabled = !previa;
        if (!previa) return;
        el("r-pecas").textContent = formato.reais(previa.valorProdutos);
        el("r-acrescimo").textContent = formato.reais(previa.acrescimo);
        el("r-total").textContent = formato.reais(previa.valorTotal);
        el("r-pago").textContent = formato.reais(previa.pagoNaVenda);
        el("r-saldo").textContent = formato.reais(previa.saldoAPagar);
        el("r-parcelas").replaceChildren(...previa.parcelas.map((p) => {
            const li = criar("li");
            const esquerda = criar("span", null, `${p.numero}ª · ${formato.data(p.vencimento)}`);
            if (p.pagaNaVenda) esquerda.append(criar("span", "chip-pago", "paga agora"));
            li.append(esquerda, criar("span", null, formato.reais(p.valor)));
            return li;
        }));
    }

    botaoConfirmar.addEventListener("click", async () => {
        if (!previa) return;
        const dados = pedido();
        const resumo = `${previa.clienteNome}: ${formato.reais(previa.valorTotal)} em ${formato.plural(previa.parcelas.length, "parcela", "parcelas")}`;
        if (!await perguntar({ titulo: "Confirmar a venda?", texto: resumo, sim: "Confirmar venda" })) return;
        botaoConfirmar.disabled = true;
        botaoConfirmar.textContent = "Registrando…";
        try {
            const criada = await api("/api/vendas", { metodo: "POST", corpo: dados });
            concluir(criada, dados.formaPagamento);
        } catch (erro) {
            erroNoResumo(erro.message);
            botaoConfirmar.disabled = false;
            atualizarPrevia(); // a situação das peças pode ter mudado
        } finally {
            botaoConfirmar.textContent = "Confirmar venda";
        }
    });

    function concluir(criada, formaPagamento) {
        const v = criada.venda;
        const caixa = el("venda-concluida");
        const pagoTexto = Number(v.pagoNaVenda) > 0
            ? `Recebido agora: ${formato.reais(v.pagoNaVenda)} (${NOMES_FORMA[formaPagamento] || formaPagamento}). `
            : "";
        caixa.replaceChildren(
            criar("strong", null, `Venda #${criada.vendaId} registrada para ${v.clienteNome}.`),
            criar("p", null, `${formato.plural(v.itens.length, "peça", "peças")}, total ${formato.reais(v.valorTotal)}. `
                + pagoTexto + (Number(v.saldoAPagar) > 0 ? `A receber: ${formato.reais(v.saldoAPagar)}.` : "Venda quitada.")));
        const nova = criar("button", null, "Fazer outra venda");
        nova.type = "button";
        nova.addEventListener("click", () => location.reload());
        const acoes = criar("div", "acoes");
        acoes.append(nova);
        caixa.append(acoes);
        caixa.hidden = false;
        el("venda-form").hidden = true;
        window.scrollTo({ top: 0, behavior: "smooth" });
        carregarRecentes();
    }

    // ---- carregar clientes e vendas recentes ---------------------------------------------

    async function carregarClientes() {
        try {
            const clientes = await api("/api/clientes");
            selCliente.replaceChildren(new Option(clientes.length ? "Escolha a cliente" : "Nenhuma cliente cadastrada", ""));
            clientes.forEach((c) => selCliente.append(new Option(`${c.nome} · ${c.telefone}`, String(c.id))));
            const daUrl = new URLSearchParams(location.search).get("cliente");
            if (daUrl) selCliente.value = daUrl;
        } catch (erro) {
            selCliente.replaceChildren(new Option("Erro ao carregar clientes", ""));
            erroNoResumo(erro.message);
        }
    }

    async function carregarRecentes() {
        const estado = el("recentes-estado");
        try {
            const vendas = await api("/api/vendas?limite=10");
            el("recentes").replaceChildren(...vendas.map((v) => {
                const tr = criar("tr");
                tr.append(criar("td", null, `#${v.vendaId}`), criar("td", null, formato.data(v.dataVenda)),
                    criar("td", null, v.clienteNome), criar("td", "numero", String(v.quantidadePecas)),
                    criar("td", "numero", formato.reais(v.valorTotal)), criar("td", "numero", formato.reais(v.pago)),
                    criar("td", "numero", formato.reais(v.saldo)));
                return tr;
            }));
            el("tabela-recentes").hidden = vendas.length === 0;
            estado.hidden = vendas.length > 0;
            estado.textContent = "Nenhuma venda registrada ainda.";
        } catch (erro) {
            estado.hidden = false;
            estado.textContent = "Não foi possível carregar as vendas recentes. " + erro.message;
        }
    }

    selCliente.addEventListener("change", atualizarPrevia);
    document.querySelectorAll('input[name="forma"]').forEach((r) => r.addEventListener("change", atualizarPrevia));
    [selParcelas, selFormaPagamento, chkAcrescimo].forEach((c) => c.addEventListener("change", atualizarPrevia));
    inpEntrada.addEventListener("input", atualizarPrevia);

    atualizarCamposPagamento();
    carregarClientes();
    carregarRecentes();
})();
