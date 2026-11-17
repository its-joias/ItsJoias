// Ficha da maleta (maleta.html?id=1): os números (GET /api/maletas, a mesma lista do Painel),
// as peças (GET /api/pecas?maletaId=&situacao=TODAS), cada uma disponível com o botão Vender
// (js/venda-rapida.js), e um resumo por categoria (só contagem de peças).
(function () {
    const { api, formato, criar } = window.ItsJoias;
    const venda = window.ItsJoiasVendaRapida;
    const el = (id) => document.getElementById(id);
    const id = Number(new URLSearchParams(location.search).get("id"));

    const SITUACOES = { DISPONIVEL: "Disponível", VENDIDA: "Vendida", PERDIDA: "Perdida", DEVOLVIDA: "Devolvida", SEM_ENTRADA: "Sem entrada" };
    const TOM = { DISPONIVEL: "verde", VENDIDA: "salmao", PERDIDA: "neutra", DEVOLVIDA: "neutra", SEM_ENTRADA: "neutra" };

    let maleta = null;
    let pecas = [];

    function indicador(rotulo, valor, nota, tom) {
        const d = criar("div", "indicador" + (tom ? " indicador--" + tom : ""));
        d.append(criar("span", "indicador-rotulo", rotulo), criar("strong", "indicador-valor", valor));
        if (nota) d.append(criar("span", "indicador-nota", nota));
        return d;
    }

    function desenharFicha() {
        document.title = `${maleta.nome} | Organização Its Joias`;
        el("maleta-nome").textContent = maleta.nome;
        const partes = [maleta.codigo, maleta.fornecedor, "entrada em " + formato.data(maleta.dataEntrada)];
        if (maleta.dataAcerto) partes.push("acerto em " + formato.data(maleta.dataAcerto));
        el("maleta-info").textContent = partes.join(" · ");
        el("maleta-situacao").replaceChildren(criar("span", "pilula pilula--verde", "Ativa"));
        el("maleta-indicadores").replaceChildren(
            indicador("Peças recebidas", String(maleta.pecasRecebidas), null, "areia"),
            indicador("Vendidas", String(maleta.pecasVendidas), null, "salmao"),
            indicador("Disponíveis", String(maleta.pecasDisponiveis), null, "verde"),
            indicador("Perdidas ou devolvidas", String(maleta.pecasPerdidasOuDevolvidas), null, "areia"),
            indicador("Custo", formato.reais(maleta.custoTotal), null, "pessego"),
            indicador("Vendido", formato.reais(maleta.totalVendido), null, "pessego"));
        el("maleta-imprimir").hidden = false;
    }

    function desenharPecas() {
        const termo = el("pecas-busca").value.trim().toLowerCase();
        const situacao = el("pecas-situacao").value;
        const visiveis = pecas.filter((p) => (!situacao || p.situacao === situacao)
            && (!termo || p.etiqueta.toLowerCase().includes(termo) || p.produto.toLowerCase().includes(termo)));
        el("pecas-lista").replaceChildren(...visiveis.map((p) => {
            const tr = criar("tr", venda.tem(p.etiqueta) ? "na-venda" : null);
            const sit = criar("td");
            sit.append(criar("span", "pilula pilula--" + (TOM[p.situacao] || "neutra"), SITUACOES[p.situacao] || p.situacao));
            const acao = criar("td", "acoes-linha");
            if (p.situacao === "DISPONIVEL") {
                const b = criar("button", "botao-pequeno", venda.tem(p.etiqueta) ? "Na venda" : "Vender");
                b.type = "button";
                b.setAttribute("aria-label", `Vender ${p.etiqueta}, ${p.produto}`);
                b.addEventListener("click", () => {
                    venda.vender(p);
                    desenharPecas();
                });
                acao.append(b);
            }
            const etiqueta = criar("td");
            etiqueta.append(criar("strong", "etiqueta-codigo", p.etiqueta));
            tr.append(etiqueta, criar("td", null, p.produto), criar("td", null, p.categoria), sit,
                criar("td", "numero", formato.reais(p.preco)), acao);
            return tr;
        }));
        el("pecas-caixa").hidden = visiveis.length === 0;
        const estado = el("pecas-estado");
        estado.hidden = visiveis.length > 0;
        estado.className = "vazio";
        estado.textContent = pecas.length ? "Nenhuma peça com essa busca." : "Esta maleta não tem peças.";
    }

    function desenharResumo() {
        const porCategoria = new Map();
        pecas.forEach((p) => {
            const c = porCategoria.get(p.categoria) || { total: 0, DISPONIVEL: 0, VENDIDA: 0, outras: 0 };
            c.total += 1;
            if (p.situacao === "DISPONIVEL" || p.situacao === "VENDIDA") c[p.situacao] += 1;
            else c.outras += 1;
            porCategoria.set(p.categoria, c);
        });
        const linhas = [...porCategoria.entries()].sort((a, b) => b[1].total - a[1].total);
        el("resumo-lista").replaceChildren(...linhas.map(([categoria, c]) => {
            const tr = criar("tr");
            tr.append(criar("td", null, categoria), criar("td", "numero", String(c.total)),
                criar("td", "numero", String(c.DISPONIVEL)), criar("td", "numero", String(c.VENDIDA)),
                criar("td", "numero", String(c.outras)));
            return tr;
        }));
    }

    async function carregarPecas() {
        const estado = el("pecas-estado");
        estado.hidden = false;
        estado.className = "carregando-texto";
        estado.textContent = "Carregando peças…";
        try {
            const todas = await api(`/api/pecas?maletaId=${id}&situacao=TODAS&limite=500`);
            // um servidor de antes do filtro ignora o maletaId: fica só com as desta maleta
            pecas = todas.filter((p) => p.maleta === maleta.nome || p.maleta === maleta.codigo);
            desenharPecas();
            desenharResumo();
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as peças. " + erro.message;
        }
    }

    async function recarregar() {
        const maletas = await api("/api/maletas");
        maleta = maletas.find((m) => m.maletaId === id) || maleta;
        desenharFicha();
        await carregarPecas();
    }

    async function iniciar() {
        const estado = el("maleta-estado-pagina");
        if (!id) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Maleta não informada. Volte para Maletas e clique no nome de uma maleta.";
            return;
        }
        try {
            const maletas = await api("/api/maletas");
            maleta = maletas.find((m) => m.maletaId === id);
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível abrir esta maleta. " + erro.message;
            return;
        }
        if (!maleta) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Esta maleta não está entre as ativas (pode ter sido encerrada ou descartada).";
            return;
        }
        estado.hidden = true;
        desenharFicha();
        el("maleta-abas").hidden = false;
        window.ItsJoiasAbas.ligar(el("maleta-abas"));
        await carregarPecas();
    }

    el("pecas-busca").addEventListener("input", desenharPecas);
    el("pecas-situacao").addEventListener("change", desenharPecas);
    el("maleta-imprimir").addEventListener("click", () => {
        el("pecas-situacao").value = "";
        el("pecas-busca").value = "";
        desenharPecas();
        window.print();
    });
    document.addEventListener("itsjoias:venda-feita", recarregar);

    iniciar();
})();
