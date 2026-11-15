// Venda rápida: o botão "Vender" de uma peça (na maleta ou em Produtos) abre esta gaveta ao lado.
// Dá para juntar mais peças antes de confirmar. Total e parcelas vêm do back-end
// (POST /api/vendas/previa) e a venda é gravada por ele (POST /api/vendas), como na tela Nova venda.
//   ItsJoiasVendaRapida.vender(peca)   peca: { etiqueta, produto, preco }
// Depois de gravar, avisa a página com o evento "itsjoias:venda-feita" (para recarregar as listas).
(function () {
    const { api, formato, criar, notificar } = window.ItsJoias;
    const { reaisDigitados } = window.ItsJoiasMascaras;

    const FORMAS = [["PIX", "Pix"], ["DINHEIRO", "Dinheiro"], ["CARTAO", "Cartão"], ["TRANSFERENCIA", "Transferência"], ["OUTRO", "Outro"]];
    const MAX_PARCELAS = 24;

    /** Peças escolhidas: { etiqueta, produto, preco }. */
    let pecas = [];
    let previa = null;
    let gaveta = null;
    let campos = {};
    let clientesCarregadas = false;

    function montar() {
        gaveta = criar("aside", "gaveta-venda");
        gaveta.setAttribute("role", "dialog");
        gaveta.setAttribute("aria-labelledby", "gaveta-titulo");
        gaveta.hidden = true;
        gaveta.innerHTML = `
            <div class="gaveta-cabecalho">
                <h3 id="gaveta-titulo" tabindex="-1">Venda rápida</h3>
                <button type="button" class="botao-fechar" aria-label="Fechar a venda rápida">✕</button>
            </div>
            <div class="gaveta-corpo">
                <ul class="gaveta-pecas" aria-label="Peças desta venda"></ul>
                <p class="texto-suave gaveta-dica">Para juntar outra peça, clique em Vender nela.</p>

                <div class="campo">
                    <label for="gv-cliente">Cliente</label>
                    <select id="gv-cliente"><option value="">Carregando clientes…</option></select>
                    <span class="texto-suave">Não está na lista? <a href="clientes.html">Cadastre a cliente</a>.</span>
                </div>

                <fieldset class="gaveta-forma">
                    <legend class="rotulo">Pagamento</legend>
                    <div class="formas-venda">
                        <label><input type="radio" name="gv-forma" value="A_VISTA" checked><strong>À vista</strong><span>Paga tudo agora</span></label>
                        <label><input type="radio" name="gv-forma" value="PARCELADO"><strong>Parcelado</strong><span>Todo dia 5</span></label>
                    </div>
                </fieldset>

                <div class="gaveta-linha">
                    <div class="campo" data-gv="parcelas" hidden>
                        <label for="gv-parcelas">Parcelas</label>
                        <select id="gv-parcelas"></select>
                    </div>
                    <div class="campo" data-gv="entrada" hidden>
                        <label for="gv-entrada">Entrada <span class="opcional">(opcional)</span></label>
                        <input type="text" id="gv-entrada" inputmode="decimal" autocomplete="off" placeholder="Ex.: 50,00…">
                    </div>
                </div>
                <div class="campo" data-gv="forma-pagamento">
                    <label for="gv-forma-pagamento" id="gv-rotulo-forma">Forma de pagamento</label>
                    <select id="gv-forma-pagamento"></select>
                </div>
                <label class="opcao"><input type="checkbox" id="gv-acrescimo"> Acrescentar 10% ao valor das peças</label>

                <div class="gaveta-resumo" aria-live="polite"></div>
                <div class="mensagem mensagem--erro" role="alert" hidden></div>
            </div>
            <div class="gaveta-rodape">
                <button type="button" class="botao-secundario" data-gv="limpar">Limpar</button>
                <button type="button" class="confirmar" data-gv="confirmar" disabled>Confirmar venda</button>
            </div>`;
        document.body.append(gaveta);

        const q = (s) => gaveta.querySelector(s);
        campos = {
            titulo: q("#gaveta-titulo"), lista: q(".gaveta-pecas"), cliente: q("#gv-cliente"), parcelas: q("#gv-parcelas"),
            entrada: q("#gv-entrada"), formaPagamento: q("#gv-forma-pagamento"), acrescimo: q("#gv-acrescimo"),
            resumo: q(".gaveta-resumo"), erro: q(".mensagem--erro"), confirmar: q('[data-gv="confirmar"]'),
        };
        for (let n = 1; n <= MAX_PARCELAS; n++) campos.parcelas.append(new Option(n === 1 ? "1 parcela" : `${n} parcelas`, String(n)));
        campos.parcelas.value = "3";
        FORMAS.forEach(([valor, nome]) => campos.formaPagamento.append(new Option(nome, valor)));

        q(".botao-fechar").addEventListener("click", fechar);
        q('[data-gv="limpar"]').addEventListener("click", () => { pecas = []; fechar(); });
        campos.confirmar.addEventListener("click", confirmar);
        gaveta.addEventListener("change", atualizar);
        campos.entrada.addEventListener("input", atualizar);
        gaveta.addEventListener("keydown", (e) => { if (e.key === "Escape") fechar(); });
    }

    async function carregarClientes() {
        if (clientesCarregadas) return;
        try {
            const clientes = await api("/api/clientes");
            campos.cliente.replaceChildren(new Option(clientes.length ? "Escolha a cliente" : "Nenhuma cliente cadastrada", ""));
            clientes.forEach((c) => campos.cliente.append(new Option(`${c.nome} · ${c.telefone}`, String(c.id))));
            clientesCarregadas = true;
        } catch (erro) {
            campos.cliente.replaceChildren(new Option("Erro ao carregar clientes", ""));
            mostrarErro(erro.message);
        }
    }

    function forma() {
        return gaveta.querySelector('input[name="gv-forma"]:checked').value;
    }

    function pedido() {
        const parcelado = forma() === "PARCELADO";
        const entrada = parcelado ? reaisDigitados(campos.entrada.value) : null;
        const temEntrada = parcelado && entrada !== null && !Number.isNaN(entrada) && Number(entrada) > 0;
        gaveta.querySelector('[data-gv="parcelas"]').hidden = !parcelado;
        gaveta.querySelector('[data-gv="entrada"]').hidden = !parcelado;
        gaveta.querySelector('[data-gv="forma-pagamento"]').hidden = parcelado && !temEntrada;
        gaveta.querySelector("#gv-rotulo-forma").textContent = parcelado ? "Forma de pagamento da entrada" : "Forma de pagamento";
        return {
            clienteId: campos.cliente.value ? Number(campos.cliente.value) : null,
            codigos: pecas.map((p) => p.etiqueta),
            acrescimo10: campos.acrescimo.checked,
            forma: forma(),
            numeroParcelas: parcelado ? Number(campos.parcelas.value) : null,
            entrada: Number.isNaN(entrada) ? "invalida" : entrada,
            formaPagamento: parcelado && !temEntrada ? null : campos.formaPagamento.value,
        };
    }

    function mostrarErro(texto) {
        campos.erro.textContent = texto || "";
        campos.erro.hidden = !texto;
    }

    function desenharPecas() {
        campos.lista.replaceChildren(...pecas.map((p, i) => {
            const li = criar("li");
            const info = criar("span", "gaveta-peca-info");
            info.append(criar("strong", null, p.etiqueta), criar("span", "texto-suave", p.produto));
            const tirar = criar("button", "botao-fechar", "✕");
            tirar.type = "button";
            tirar.setAttribute("aria-label", `Tirar ${p.etiqueta} da venda`);
            tirar.addEventListener("click", () => {
                pecas.splice(i, 1);
                if (pecas.length === 0) fechar(); else { desenharPecas(); atualizar(); }
            });
            li.append(info, criar("span", "dinheiro", formato.reais(p.preco)), tirar);
            return li;
        }));
        campos.titulo.textContent = pecas.length === 1 ? "Vender 1 peça" : `Vender ${pecas.length} peças`;
    }

    function desenharResumo() {
        if (!previa) {
            campos.resumo.replaceChildren();
            return;
        }
        const linhas = criar("dl", "resumo-linhas");
        const linha = (rotulo, valor, classe) => {
            linhas.append(criar("dt", classe, rotulo), criar("dd", classe, valor));
        };
        linha("Peças", formato.reais(previa.valorProdutos));
        if (Number(previa.acrescimo) > 0) linha("Acréscimo de 10%", formato.reais(previa.acrescimo));
        linha("Total", formato.reais(previa.valorTotal), "total");
        const blocos = [linhas];
        const aPrazo = previa.parcelas.filter((p) => !p.pagaNaVenda);
        if (Number(previa.pagoNaVenda) > 0) blocos.push(criar("p", null, `Recebe agora: ${formato.reais(previa.pagoNaVenda)}`));
        if (aPrazo.length) {
            const lista = criar("ul", "parcelas-previa");
            aPrazo.forEach((p) => {
                const li = criar("li");
                li.append(criar("span", null, `${p.numero}ª · ${formato.data(p.vencimento)}`), criar("span", null, formato.reais(p.valor)));
                lista.append(li);
            });
            blocos.push(lista);
        }
        campos.resumo.replaceChildren(...blocos);
    }

    let pedidoAtual = 0;
    let espera;

    function atualizar() {
        clearTimeout(espera);
        espera = setTimeout(pedirPrevia, 250);
    }

    async function pedirPrevia() {
        const dados = pedido();
        const meu = ++pedidoAtual;
        campos.confirmar.disabled = true;
        previa = null;
        if (!dados.clienteId) {
            mostrarErro("");
            desenharResumo();
            return;
        }
        if (dados.entrada === "invalida") {
            mostrarErro("Valor da entrada inválido. Use, por exemplo, 50,00.");
            desenharResumo();
            return;
        }
        try {
            const resposta = await api("/api/vendas/previa", { metodo: "POST", corpo: dados });
            if (meu !== pedidoAtual) return;
            previa = resposta;
            mostrarErro("");
            campos.confirmar.disabled = false;
        } catch (erro) {
            if (meu !== pedidoAtual) return;
            mostrarErro(erro.message);
        }
        desenharResumo();
    }

    async function confirmar() {
        const dados = pedido();
        campos.confirmar.disabled = true;
        campos.confirmar.classList.add("processando");
        try {
            const criada = await api("/api/vendas", { metodo: "POST", corpo: dados });
            notificar({
                tipo: "sucesso", titulo: `Venda #${criada.vendaId} registrada.`,
                texto: `${criada.venda.clienteNome}: ${formato.plural(criada.venda.itens.length, "peça", "peças")}, `
                    + `${formato.reais(criada.venda.valorTotal)}.`,
            });
            pecas = [];
            campos.entrada.value = "";
            campos.acrescimo.checked = false;
            fechar();
            document.dispatchEvent(new CustomEvent("itsjoias:venda-feita", { detail: criada }));
        } catch (erro) {
            mostrarErro(erro.message);
            campos.confirmar.disabled = false;
        } finally {
            campos.confirmar.classList.remove("processando");
        }
    }

    function fechar() {
        if (!gaveta) return;
        gaveta.hidden = true;
        document.body.classList.remove("com-gaveta");
    }

    /** Põe a peça na venda e abre a gaveta (se a peça já está nela, só abre). */
    function vender(peca) {
        if (!gaveta) montar();
        if (!pecas.some((p) => p.etiqueta === peca.etiqueta)) {
            pecas.push({ etiqueta: peca.etiqueta, produto: peca.produto, preco: peca.preco });
        }
        const estavaFechada = gaveta.hidden;
        gaveta.hidden = false;
        document.body.classList.add("com-gaveta");
        desenharPecas();
        carregarClientes().then(atualizar);
        if (estavaFechada) campos.titulo.focus();
    }

    function tem(etiqueta) {
        return pecas.some((p) => p.etiqueta === etiqueta);
    }

    window.ItsJoiasVendaRapida = { vender, tem };
})();
