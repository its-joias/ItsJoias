// Maletas ativas, direto do banco (GET /api/maletas). Clicar no nome abre as peças da maleta
// (GET /api/pecas?maletaId=), cada uma com o botão Vender (js/venda-rapida.js).
(function () {
    const { api, formato, criar, perguntar, notificar } = window.ItsJoias;
    const venda = window.ItsJoiasVendaRapida;
    const estado = document.getElementById("estado");
    const tabela = document.getElementById("tabela");

    /** "Maleta acerto 07/12/2026" -> "2026-12-07" (servidor antigo, que ainda não manda dataAcerto). */
    function acertoPeloNome(nome) {
        const d = /acerto\D{0,3}(\d{2})\/(\d{2})\/(\d{4})/i.exec(nome || "");
        return d ? `${d[3]}-${d[2]}-${d[1]}` : null;
    }

    /** Dia da importação de cada maleta pelo histórico (servidor antigo, que ainda não manda importadaEm). */
    async function importacoesPorMaleta() {
        const dias = new Map();
        try {
            const lista = await api("/api/importacoes?limite=100");
            lista.forEach((h) => {
                if (!dias.has(h.maletaId)) {
                    dias.set(h.maletaId, new Date(h.data).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" }));
                }
            });
        } catch (erro) {
            // sem o histórico, a linha só não mostra o dia da importação
        }
        return dias;
    }

    async function carregar() {
        try {
            const maletas = await api("/api/maletas");
            const precisaHistorico = maletas.some((m) => m.importadaEm === undefined);
            const historico = precisaHistorico ? await importacoesPorMaleta() : new Map();
            document.getElementById("lista").replaceChildren(...maletas.map((m) => {
                const tr = criar("tr");
                const nome = criar("td");
                const abrir = criar("button", "nome-maleta-botao", m.nome);
                abrir.type = "button";
                abrir.title = "Ver as peças desta maleta";
                abrir.addEventListener("click", () => abrirMaleta(m));
                const importada = m.importadaEm ? formato.data(m.importadaEm) : historico.get(m.maletaId);
                nome.append(abrir);
                if (importada) {
                    nome.append(criar("div", "maleta-importada", "Importada em " + importada));
                }
                nome.append(criar("div", "texto-suave", m.codigo));
                const acerto = m.dataAcerto !== undefined ? m.dataAcerto : acertoPeloNome(m.nome);
                tr.append(nome, criar("td", null, m.fornecedor), criar("td", null, formato.data(m.dataEntrada)),
                    criar("td", null, acerto ? formato.data(acerto) : "—"),
                    criar("td", "numero", String(m.pecasRecebidas)), criar("td", "numero", String(m.pecasVendidas)),
                    criar("td", "numero", String(m.pecasDisponiveis)), criar("td", "numero", String(m.pecasPerdidasOuDevolvidas)),
                    criar("td", "numero", formato.reais(m.custoTotal)), criar("td", "numero", formato.reais(m.totalVendido)),
                    acoes(m));
                return tr;
            }));
            tabela.hidden = maletas.length === 0;
            estado.hidden = maletas.length > 0;
            estado.className = "vazio";
            estado.textContent = "Nenhuma maleta ativa cadastrada ainda.";
        } catch (erro) {
            tabela.hidden = true;
            estado.hidden = false;
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as maletas. " + erro.message;
        }
    }

    /** Descartar maleta importada errada: só a proprietária, e só sem peça vendida. */
    function acoes(m) {
        const td = criar("td", "acoes-linha");
        td.dataset.somente = "PROPRIETARIA";
        const botao = criar("button", "botao-perigo botao-pequeno", "Descartar");
        botao.type = "button";
        if (m.pecasVendidas > 0) {
            botao.disabled = true;
            botao.title = "Tem peça vendida: cancele as vendas antes de descartar.";
        } else {
            botao.title = "Importou errado? Descarta a maleta e libera para importar de novo.";
        }
        botao.addEventListener("click", () => descartar(m));
        td.append(botao);
        return td;
    }

    async function descartar(m) {
        const motivo = await perguntar({
            tipo: "perigo",
            titulo: `Descartar a maleta "${m.nome}"?`,
            texto: ["Use quando a maleta foi importada errada. As peças saem do estoque e a maleta deixa de aparecer nas ativas.",
                "Nada é apagado: fica no histórico como descartada. O código do pedido é liberado para importar de novo."],
            detalhes: [["Código", m.codigo], ["Peças disponíveis", String(m.pecasDisponiveis)], ["Custo", formato.reais(m.custoTotal)]],
            campo: { rotulo: "Motivo do descarte", placeholder: "Ex.: importei o PDF errado", minimo: 3 },
            sim: "Descartar maleta", nao: "Manter maleta",
        });
        if (!motivo) return;
        try {
            const r = await api(`/api/maletas/${m.maletaId}/descartar`, { metodo: "POST", corpo: { motivo } });
            notificar({ tipo: "sucesso", titulo: "Maleta descartada.", texto: `${r.nome}: ${formato.plural(r.pecasRetiradas, "peça retirada", "peças retiradas")} do estoque.` });
            carregar();
        } catch (erro) {
            notificar({ tipo: "erro", titulo: "Não foi possível descartar.", texto: erro.message });
        }
    }

    // ---- peças de uma maleta ---------------------------------------------------------------

    const SITUACOES = { DISPONIVEL: "Disponível", VENDIDA: "Vendida", PERDIDA: "Perdida", DEVOLVIDA: "Devolvida", SEM_ENTRADA: "Sem entrada" };
    const secao = document.getElementById("maleta-aberta");
    const buscaMaleta = document.getElementById("maleta-busca");
    const soDisponiveis = document.getElementById("maleta-so-disponiveis");
    const estadoMaleta = document.getElementById("maleta-estado");
    const listaMaleta = document.getElementById("maleta-pecas");
    let maletaAberta = null;
    let pecasDaMaleta = [];

    async function abrirMaleta(m) {
        maletaAberta = m;
        secao.hidden = false;
        document.getElementById("maleta-aberta-titulo").textContent = m.nome;
        document.getElementById("maleta-aberta-info").textContent = `${m.codigo} · ${m.fornecedor} · `
            + `${formato.plural(m.pecasDisponiveis, "peça disponível", "peças disponíveis")} de ${m.pecasRecebidas}`;
        secao.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
        document.getElementById("maleta-aberta-titulo").focus({ preventScroll: true });
        await carregarPecas();
    }

    async function carregarPecas() {
        if (!maletaAberta) return;
        listaMaleta.replaceChildren();
        estadoMaleta.hidden = false;
        estadoMaleta.className = "carregando-texto";
        estadoMaleta.textContent = "Carregando peças…";
        try {
            const pecas = await api(`/api/pecas?maletaId=${maletaAberta.maletaId}&situacao=TODAS&limite=200`);
            // um servidor de antes do filtro ignora o maletaId e devolve peças de todas: fica só com as desta
            pecasDaMaleta = pecas.filter((p) => p.maleta === maletaAberta.nome);
            desenharPecas();
        } catch (erro) {
            estadoMaleta.className = "mensagem mensagem--erro";
            estadoMaleta.textContent = "Não foi possível carregar as peças. " + erro.message;
        }
    }

    function desenharPecas() {
        const termo = buscaMaleta.value.trim().toLowerCase();
        const visiveis = pecasDaMaleta.filter((p) => (!soDisponiveis.checked || p.situacao === "DISPONIVEL")
            && (!termo || p.etiqueta.toLowerCase().includes(termo) || p.produto.toLowerCase().includes(termo)));
        listaMaleta.replaceChildren(...visiveis.map((p) => {
            const li = criar("li", venda.tem(p.etiqueta) ? "na-venda" : null);
            const nome = criar("div", "peca-nome", p.produto);
            nome.append(criar("span", null, p.categoria));
            li.append(criar("span", "peca-etiqueta", p.etiqueta), nome, criar("span", "dinheiro", formato.reais(p.preco)));
            if (p.situacao === "DISPONIVEL") {
                const vender = criar("button", "botao-pequeno", venda.tem(p.etiqueta) ? "Na venda" : "Vender");
                vender.type = "button";
                vender.setAttribute("aria-label", `Vender ${p.etiqueta}, ${p.produto}`);
                vender.addEventListener("click", () => {
                    venda.vender(p);
                    desenharPecas();
                });
                li.append(vender);
            } else {
                li.append(criar("span", "situacao-peca", SITUACOES[p.situacao] || p.situacao));
            }
            return li;
        }));
        estadoMaleta.hidden = visiveis.length > 0;
        estadoMaleta.className = "vazio";
        estadoMaleta.textContent = termo ? "Nenhuma peça desta maleta com essa busca."
            : soDisponiveis.checked ? "Nenhuma peça disponível nesta maleta." : "Esta maleta não tem peças.";
    }

    buscaMaleta.addEventListener("input", desenharPecas);
    soDisponiveis.addEventListener("change", desenharPecas);
    document.getElementById("maleta-fechar").addEventListener("click", () => {
        secao.hidden = true;
        maletaAberta = null;
    });
    document.addEventListener("itsjoias:venda-feita", () => {
        carregar();
        carregarPecas();
    });

    carregar();
    window.ItsJoiasMaletas = { recarregar: carregar };
})();
