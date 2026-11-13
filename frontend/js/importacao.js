// Importação de maleta por PDF.
// 1) escolher o PDF  2) o back lê e devolve a prévia  3) conferir/corrigir (o back confere de novo a cada
// alteração)  4) confirmar (o back grava tudo de uma vez). Nenhuma conta nem regra é decidida aqui.
(function () {
    const { api, formato, criar, perguntar, notificar } = window.ItsJoias;
    const { reaisDigitados } = window.ItsJoiasMascaras;
    const el = (id) => document.getElementById(id);

    const TAMANHO_MAXIMO = 15 * 1024 * 1024;
    const NOVO_FORNECEDOR = "novo";
    const formatoValor = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const secao = el("importacao");
    const inputArquivo = el("imp-arquivo");
    const botaoLer = el("imp-ler");
    const botaoConfirmar = el("imp-confirmar");
    const corpoLinhas = el("imp-linhas");

    /** Última prévia conferida pelo servidor. */
    let previa = null;
    /** Dados somente-leitura de cada linha (como foi lida), por índice. */
    let lidas = new Map();
    let arquivoEscolhido = null;

    // ---- abrir / fechar ------------------------------------------------------------------

    function mostrarPasso(passo) {
        el("imp-passo-arquivo").hidden = passo !== "arquivo";
        el("imp-carregando").hidden = passo !== "carregando";
        el("imp-previa").hidden = passo !== "previa";
        el("imp-concluido").hidden = passo !== "concluido";
    }

    function abrir() {
        previa = null;
        arquivoEscolhido = null;
        inputArquivo.value = "";
        el("imp-arquivo-nome").textContent = "Clique para escolher o PDF";
        botaoLer.disabled = true;
        esconderMensagem();
        mostrarPasso("arquivo");
        secao.hidden = false;
        secao.scrollIntoView({ behavior: "smooth", block: "start" });
    }

    async function fechar() {
        if (previa && !el("imp-previa").hidden && !await perguntar({
            titulo: "Cancelar a importação?",
            texto: "Nada foi salvo. As correções feitas nesta tela serão perdidas.",
            sim: "Cancelar importação", nao: "Continuar conferindo", perigo: true,
        })) {
            return;
        }
        previa = null;
        secao.hidden = true;
    }

    el("botao-importar").addEventListener("click", abrir);
    document.querySelectorAll("[data-imp-cancelar]").forEach((b) => b.addEventListener("click", fechar));
    el("imp-outra").addEventListener("click", abrir);
    el("imp-imprimir").addEventListener("click", () => window.print());

    // ---- mensagens -----------------------------------------------------------------------

    function mostrarMensagem(tipo, titulo, texto) {
        const m = el("imp-mensagem");
        m.className = "mensagem mensagem--" + tipo;
        m.replaceChildren(criar("strong", null, titulo));
        if (texto) m.append(criar("p", null, texto));
        m.hidden = false;
        m.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function esconderMensagem() {
        el("imp-mensagem").hidden = true;
    }

    /** Avisos gerais do arquivo (duplicidade, soma diferente, OCR) em caixas de destaque, um por situação. */
    function caixasDeAviso(avisos) {
        const TITULOS = { ERRO: "Atenção", ALERTA: "Confira antes de confirmar", INFO: "Para saber" };
        const TIPOS = { ERRO: "erro", ALERTA: "alerta", INFO: "info" };
        return avisos.map((a) => {
            const caixa = criar("div", "mensagem mensagem--" + TIPOS[a.nivel]);
            caixa.append(criar("strong", null, TITULOS[a.nivel]), criar("p", null, a.mensagem));
            return caixa;
        });
    }

    function listaAvisos(avisos) {
        const ul = criar("ul", "lista-avisos");
        avisos.forEach((a) => {
            const li = criar("li", "aviso aviso--" + a.nivel.toLowerCase());
            li.append(criar("span", "aviso-icone", a.nivel === "ERRO" ? "✖" : a.nivel === "ALERTA" ? "!" : "i"),
                criar("span", null, a.mensagem));
            ul.append(li);
        });
        return ul;
    }

    // ---- passo 1: escolher e ler ---------------------------------------------------------

    function escolher(arquivo) {
        esconderMensagem();
        if (!arquivo) return;
        if (!/\.pdf$/i.test(arquivo.name) && arquivo.type !== "application/pdf") {
            mostrarMensagem("erro", "Este arquivo não é um PDF.", "Escolha o PDF do termo de entrega da maleta.");
            return;
        }
        if (arquivo.size > TAMANHO_MAXIMO) {
            mostrarMensagem("erro", "Arquivo grande demais.", "O limite é 15 MB.");
            return;
        }
        arquivoEscolhido = arquivo;
        el("imp-arquivo-nome").textContent = arquivo.name;
        botaoLer.disabled = false;
    }

    inputArquivo.addEventListener("change", () => escolher(inputArquivo.files[0]));
    const area = document.querySelector(".area-arquivo");
    ["dragenter", "dragover"].forEach((ev) => area.addEventListener(ev, (e) => {
        e.preventDefault();
        area.classList.add("area-arquivo--sobre");
    }));
    ["dragleave", "drop"].forEach((ev) => area.addEventListener(ev, (e) => {
        e.preventDefault();
        area.classList.remove("area-arquivo--sobre");
    }));
    area.addEventListener("drop", (e) => escolher(e.dataTransfer.files[0]));

    const AVISO_DEMORA = "PDF que é foto ou escaneado leva alguns minutos (até uns 8). Pode deixar esta tela aberta.";
    const pausa = (ms) => new Promise((r) => setTimeout(r, ms));

    /** O servidor lê em segundo plano; a tela pergunta a situação até ficar pronta (ou dar erro). */
    async function acompanharLeitura(situacao) {
        let falhasSeguidas = 0;
        while (situacao.situacao === "NA_FILA" || situacao.situacao === "LENDO") {
            await pausa(3000);
            try {
                situacao = await api("/api/importacoes/leituras/" + encodeURIComponent(situacao.id));
                falhasSeguidas = 0;
            } catch (erro) {
                // internet oscilou: tenta mais algumas vezes antes de desistir (a leitura continua no servidor)
                if (erro.status === 404 || ++falhasSeguidas >= 5) throw erro;
            }
        }
        if (situacao.situacao === "ERRO") throw new Error(situacao.mensagem);
        return situacao.previa;
    }

    botaoLer.addEventListener("click", async () => {
        if (!arquivoEscolhido) return;
        esconderMensagem();
        mostrarPasso("carregando");
        const inicio = Date.now();
        const texto = el("imp-carregando-texto");
        texto.textContent = AVISO_DEMORA;
        const relogio = setInterval(() => {
            const s = Math.round((Date.now() - inicio) / 1000);
            const tempo = s < 60 ? `${s} s` : `${Math.floor(s / 60)} min ${String(s % 60).padStart(2, "0")} s`;
            texto.textContent = `${tempo}. ${AVISO_DEMORA}`;
        }, 1000);
        try {
            const dados = new FormData();
            dados.append("arquivo", arquivoEscolhido);
            const inicial = await api("/api/importacoes/ler", { metodo: "POST", corpo: dados });
            const previaLida = await acompanharLeitura(inicial);
            desenharPrevia(previaLida);
            mostrarPasso("previa");
        } catch (erro) {
            mostrarPasso("arquivo");
            mostrarMensagem("erro", "Não foi possível ler o PDF.", erro.message);
        } finally {
            clearInterval(relogio);
            texto.textContent = AVISO_DEMORA;
        }
    });

    // ---- passo 2: prévia -----------------------------------------------------------------

    function desenharPrevia(p) {
        previa = p;
        lidas = new Map(p.linhas.map((c) => [c.linha.indice, c.linha]));

        // fornecedor e maleta
        const sel = el("imp-fornecedor");
        sel.replaceChildren(new Option("Escolha o fornecedor", ""));
        p.fornecedores.forEach((f) => sel.append(new Option(f.nome, String(f.id))));
        sel.append(new Option("+ Novo fornecedor…", NOVO_FORNECEDOR));
        const m = p.maleta;
        sel.value = m.fornecedorId ? String(m.fornecedorId) : (m.fornecedorNovoNome ? NOVO_FORNECEDOR : "");
        el("imp-fornecedor-nome").value = m.fornecedorNovoNome || "";
        el("imp-fornecedor-cnpj").value = m.fornecedorCnpj || "";
        el("imp-maleta-nome").value = m.nome || "";
        el("imp-maleta-codigo").value = m.codigo || "";
        el("imp-data-entrada").value = m.dataEntrada || "";
        el("imp-data-acerto").value = m.dataAcerto || "";
        alternarNovoFornecedor();

        corpoLinhas.replaceChildren(...p.linhas.map((c) => linhaTabela(c, p.categorias)));
        el("imp-reimportar").checked = false;
        el("imp-so-problemas").checked = false;
        atualizar(p);
    }

    function alternarNovoFornecedor() {
        const novo = el("imp-fornecedor").value === NOVO_FORNECEDOR;
        el("imp-campo-novo-fornecedor").hidden = !novo;
        el("imp-campo-cnpj").hidden = !novo;
    }

    function linhaTabela(c, categorias) {
        const l = c.linha;
        const tr = criar("tr");
        tr.dataset.indice = l.indice;

        const tdImportar = criar("td");
        tdImportar.dataset.rotulo = "Importar";
        const chk = criar("input");
        chk.type = "checkbox";
        chk.className = "f-importar";
        chk.checked = l.importar;
        chk.setAttribute("aria-label", `Importar a linha ${l.indice}`);
        tdImportar.append(chk, criar("span", "texto-suave numero-linha", `#${l.indice}`));

        const tdCodigo = criar("td");
        tdCodigo.dataset.rotulo = "Código do fornecedor";
        const codigo = criar("input");
        codigo.className = "f-codigo";
        codigo.value = l.codigo || "";
        codigo.inputMode = "numeric";
        codigo.setAttribute("aria-label", `Código da linha ${l.indice}`);
        const conferido = criar("label", "opcao opcao-pequena f-conferido-campo");
        const chkConf = criar("input");
        chkConf.type = "checkbox";
        chkConf.className = "f-conferido";
        chkConf.checked = l.codigoConferido;
        conferido.append(chkConf, " conferido no papel");
        tdCodigo.append(codigo, conferido);

        const tdDescricao = criar("td");
        tdDescricao.dataset.rotulo = "Descrição";
        const descricao = criar("input");
        descricao.className = "f-descricao";
        descricao.value = l.descricao || "";
        descricao.maxLength = 120;
        descricao.setAttribute("aria-label", `Descrição da linha ${l.indice}`);
        tdDescricao.append(descricao);
        if (l.textoOriginal) {
            const lido = criar("span", "texto-lido", "Lido no PDF: " + l.textoOriginal);
            tdDescricao.append(lido);
        }

        const tdCategoria = criar("td");
        tdCategoria.dataset.rotulo = "Categoria";
        const cat = criar("select");
        cat.className = "f-categoria";
        cat.append(new Option("Escolha", ""));
        categorias.forEach((o) => cat.append(new Option(o.nome, String(o.id))));
        cat.value = l.categoriaId ? String(l.categoriaId) : "";
        cat.setAttribute("aria-label", `Categoria da linha ${l.indice}`);
        tdCategoria.append(cat, criar("span", "texto-suave f-modelo-existente"));

        const tdQtd = criar("td", "numero");
        tdQtd.dataset.rotulo = "Quantidade";
        const qtd = criar("input");
        qtd.className = "f-quantidade";
        qtd.type = "number";
        qtd.min = "1";
        qtd.max = "999";
        qtd.value = l.quantidade ?? "";
        qtd.setAttribute("aria-label", `Quantidade da linha ${l.indice}`);
        tdQtd.append(qtd);

        const tdValor = criar("td", "numero");
        tdValor.dataset.rotulo = "Valor unitário";
        const valor = criar("input");
        valor.className = "f-valor";
        valor.inputMode = "decimal";
        valor.value = l.valorUnitario != null ? formatoValor.format(l.valorUnitario) : "";
        valor.setAttribute("aria-label", `Valor unitário da linha ${l.indice}`);
        tdValor.append(valor);

        const tdTotal = criar("td", "numero f-total");
        tdTotal.dataset.rotulo = "Total";

        const tdConf = criar("td", "f-conferencia");
        tdConf.dataset.rotulo = "Conferência";
        const decisao = criar("select");
        decisao.className = "f-decisao";
        decisao.append(new Option("Preço diferente: escolha", ""), new Option("Manter o preço cadastrado", "MANTER"),
            new Option("Atualizar para o preço do PDF", "ATUALIZAR"));
        decisao.value = l.decisaoPreco || "";
        decisao.setAttribute("aria-label", `Decisão de preço da linha ${l.indice}`);
        tdConf.append(criar("div", "f-avisos"), decisao);

        tr.append(tdImportar, tdCodigo, tdDescricao, tdCategoria, tdQtd, tdValor, tdTotal, tdConf);
        return tr;
    }

    /** Atualiza só o que o servidor conferiu, sem recriar os campos (não perde o foco de quem digita). */
    function atualizar(p) {
        previa = p;
        el("imp-avisos-gerais").replaceChildren(...caixasDeAviso(p.avisosGerais));
        el("imp-avisos-maleta").replaceChildren(...listaAvisos(p.avisosMaleta).children);

        const r = p.resumo;
        el("imp-resumo").replaceChildren(
            resumoItem("Linhas lidas", r.linhas), resumoItem("A importar", r.aImportar),
            resumoItem("Com erro", r.comErro, r.comErro > 0), resumoItem("Peças", r.pecas),
            resumoItem("Valor", formato.reais(r.valorTotal)), resumoItem("Modelos novos", r.modelosNovos),
            resumoItem("Já cadastrados", r.modelosExistentes));

        p.linhas.forEach((c) => {
            const tr = corpoLinhas.querySelector(`tr[data-indice="${c.linha.indice}"]`);
            if (!tr) return;
            const erros = c.avisos.filter((a) => a.nivel === "ERRO");
            const alertas = c.avisos.filter((a) => a.nivel === "ALERTA");
            tr.classList.toggle("linha-erro", c.temErro);
            tr.classList.toggle("linha-alerta", !c.temErro && alertas.length > 0);
            tr.classList.toggle("linha-ignorada", !c.linha.importar);
            tr.querySelector(".f-total").textContent = c.valorTotal != null ? formato.reais(c.valorTotal) : "—";
            tr.querySelector(".f-avisos").replaceChildren(listaAvisos(c.avisos));

            const pedeConferido = erros.some((a) => a.mensagem.includes("conferido"));
            tr.querySelector(".f-conferido-campo").hidden = !pedeConferido && !tr.querySelector(".f-conferido").checked;

            const existente = c.produtoExistente;
            tr.querySelector(".f-categoria").hidden = Boolean(existente);
            tr.querySelector(".f-modelo-existente").textContent = existente
                ? `Modelo ${existente.codigo} (${existente.categoria})` : "";
            const conflitoPreco = existente && c.linha.valorUnitario != null
                && Number(existente.precoVenda) !== Number(c.linha.valorUnitario);
            tr.querySelector(".f-decisao").hidden = !conflitoPreco;
        });

        const jaImportado = p.importacoesDoMesmoArquivo.length > 0;
        el("imp-campo-reimportar").hidden = !jaImportado;
        atualizarBotaoConfirmar();
        filtrar();
    }

    function resumoItem(rotulo, valor, destaque) {
        const d = criar("div", "resumo-item" + (destaque ? " resumo-item--erro" : ""));
        d.append(criar("span", "texto-suave", rotulo), criar("strong", null, String(valor)));
        return d;
    }

    function atualizarBotaoConfirmar() {
        const jaImportado = previa && previa.importacoesDoMesmoArquivo.length > 0;
        const ok = previa && previa.podeConfirmar && (!jaImportado || el("imp-reimportar").checked);
        botaoConfirmar.disabled = !ok;
        el("imp-status-confirmar").textContent = !previa ? ""
            : previa.podeConfirmar ? (jaImportado && !el("imp-reimportar").checked
                ? "Confirme a reimportação para continuar." : "Tudo conferido.")
            : `Corrija ${previa.resumo.comErro || "os"} ponto(s) destacados em vermelho para confirmar.`;
    }

    function filtrar() {
        const so = el("imp-so-problemas").checked;
        corpoLinhas.querySelectorAll("tr").forEach((tr) => {
            tr.hidden = so && !tr.classList.contains("linha-erro") && !tr.classList.contains("linha-alerta");
        });
    }

    // ---- montar o pedido a partir dos campos ----------------------------------------------

    function pedidoAtual() {
        const fornecedor = el("imp-fornecedor").value;
        const maleta = {
            fornecedorId: fornecedor && fornecedor !== NOVO_FORNECEDOR ? Number(fornecedor) : null,
            fornecedorNovoNome: fornecedor === NOVO_FORNECEDOR ? el("imp-fornecedor-nome").value : null,
            fornecedorCnpj: fornecedor === NOVO_FORNECEDOR ? el("imp-fornecedor-cnpj").value || null : null,
            codigo: el("imp-maleta-codigo").value,
            nome: el("imp-maleta-nome").value,
            dataEntrada: el("imp-data-entrada").value || null,
            dataAcerto: el("imp-data-acerto").value || null,
        };
        const linhas = [...corpoLinhas.querySelectorAll("tr")].map((tr) => {
            const indice = Number(tr.dataset.indice);
            const lida = lidas.get(indice);
            const valor = reaisDigitados(tr.querySelector(".f-valor").value);
            const qtd = tr.querySelector(".f-quantidade").value;
            return {
                indice,
                importar: tr.querySelector(".f-importar").checked,
                codigo: tr.querySelector(".f-codigo").value.trim() || null,
                descricao: tr.querySelector(".f-descricao").value.trim() || null,
                categoriaId: tr.querySelector(".f-categoria").value ? Number(tr.querySelector(".f-categoria").value) : null,
                quantidade: qtd === "" ? null : Number.parseInt(qtd, 10),
                valorUnitario: valor === null || Number.isNaN(valor) ? null : valor,
                codigoConferido: tr.querySelector(".f-conferido").checked,
                decisaoPreco: tr.querySelector(".f-decisao").value || null,
                textoOriginal: lida.textoOriginal,
                valorTotalLido: lida.valorTotalLido,
                quantidadeDeduzida: lida.quantidadeDeduzida,
            };
        });
        return {
            arquivo: previa.arquivo, documento: previa.documento, maleta, linhas,
            confirmarReimportacao: el("imp-reimportar").checked,
        };
    }

    // ---- validar a cada alteração ---------------------------------------------------------

    let espera;
    let pedido = 0;

    function agendarValidacao() {
        botaoConfirmar.disabled = true;
        el("imp-status-confirmar").textContent = "Conferindo…";
        clearTimeout(espera);
        espera = setTimeout(validar, 450);
    }

    async function validar() {
        if (!previa) return;
        const meu = ++pedido;
        try {
            const resposta = await api("/api/importacoes/validar", { metodo: "POST", corpo: pedidoAtual() });
            if (meu === pedido) atualizar(resposta);
        } catch (erro) {
            if (meu === pedido) mostrarMensagem("erro", "Não foi possível conferir as alterações.", erro.message);
        }
    }

    el("imp-previa").addEventListener("input", (e) => {
        if (e.target.matches("input[type=text], input:not([type]), input[type=number], input[type=date]")) agendarValidacao();
    });
    el("imp-previa").addEventListener("change", (e) => {
        if (e.target.id === "imp-fornecedor") alternarNovoFornecedor();
        if (e.target.id === "imp-so-problemas") {
            filtrar();
            return;
        }
        if (e.target.id === "imp-reimportar") {
            atualizarBotaoConfirmar();
            return;
        }
        if (e.target.matches("select, input[type=checkbox], input[type=date]")) agendarValidacao();
    });

    // ---- passo 3: confirmar --------------------------------------------------------------

    botaoConfirmar.addEventListener("click", async () => {
        if (!previa) return;
        const r = previa.resumo;
        const reimportando = previa.importacoesDoMesmoArquivo.length > 0;
        if (!await perguntar({
            tipo: reimportando ? "alerta" : "importar",
            titulo: reimportando ? "Importar este arquivo de novo?" : "Confirmar a importação?",
            texto: reimportando
                ? "Este PDF já foi importado antes. As peças serão cadastradas outra vez, com etiquetas novas."
                : "Cada peça receberá uma etiqueta própria. Depois de confirmar, a importação fica no histórico.",
            detalhes: [["Maleta", el("imp-maleta-nome").value], ["Peças", String(r.pecas)],
                ["Modelos novos", String(r.modelosNovos)], ["Valor", formato.reais(r.valorTotal)]],
            sim: reimportando ? "Importar de novo" : "Confirmar importação",
        })) return;
        botaoConfirmar.disabled = true;
        botaoConfirmar.classList.add("processando");
        esconderMensagem();
        try {
            const resultado = await api("/api/importacoes/confirmar", { metodo: "POST", corpo: pedidoAtual() });
            mostrarConcluido(resultado);
        } catch (erro) {
            mostrarMensagem("erro", "A importação não foi feita.", erro.message + " Nada foi salvo.");
            validar();
        } finally {
            botaoConfirmar.classList.remove("processando");
        }
    });

    function mostrarConcluido(r) {
        previa = null;
        el("imp-sucesso").replaceChildren(
            criar("strong", null, `Maleta "${r.maletaNome}" importada.`),
            criar("p", null, `${formato.plural(r.pecas, "peça cadastrada", "peças cadastradas")} `
                + `(${formato.reais(r.valorTotal)}), ${formato.plural(r.modelosNovos, "modelo novo", "modelos novos")}`
                + (r.linhasIgnoradas ? `, ${formato.plural(r.linhasIgnoradas, "linha ignorada", "linhas ignoradas")}.` : ".")));
        el("imp-etiquetas").replaceChildren(...r.etiquetas.map((e) => {
            const tr = criar("tr");
            const etq = criar("td");
            etq.append(criar("strong", null, e.etiqueta));
            tr.append(etq, criar("td", null, e.produto), criar("td", null, e.codigoFornecedor || "—"),
                criar("td", "numero", formato.reais(e.preco)));
            return tr;
        }));
        mostrarPasso("concluido");
        notificar({ tipo: "sucesso", titulo: "Importação concluída.", texto: `${r.maletaNome}: ${formato.plural(r.pecas, "peça", "peças")}.` });
        secao.scrollIntoView({ behavior: "smooth", block: "start" });
        if (window.ItsJoiasMaletas) window.ItsJoiasMaletas.recarregar();
        carregarHistorico();
    }

    // ---- histórico ------------------------------------------------------------------------

    async function carregarHistorico() {
        const estado = el("hist-estado");
        try {
            const lista = await api("/api/importacoes?limite=20");
            el("hist-linhas").replaceChildren(...lista.map((h) => {
                const tr = criar("tr");
                const data = new Date(h.data).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
                tr.append(criar("td", null, data), criar("td", null, h.arquivo), criar("td", null, h.maleta),
                    criar("td", null, h.fornecedor), criar("td", null, h.usuario), criar("td", "numero", String(h.pecas)),
                    criar("td", "numero", String(h.modelosNovos)), criar("td", "numero", formato.reais(h.valorTotal)));
                return tr;
            }));
            el("hist-tabela").hidden = lista.length === 0;
            estado.hidden = lista.length > 0;
            estado.className = "vazio";
            estado.textContent = "Nenhuma importação feita ainda.";
        } catch (erro) {
            estado.hidden = false;
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar o histórico. " + erro.message;
        }
    }

    carregarHistorico();
})();
