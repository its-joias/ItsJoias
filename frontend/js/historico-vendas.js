// Aba Vendas: todas as vendas (GET /api/vendas?busca=), com filtros e o cartão de cada venda
// (js/venda-detalhe.js), onde ficam Receber pagamento, Devolução e Cancelar venda.
(function () {
    const { api, criar } = window.ItsJoias;
    const el = (id) => document.getElementById(id);
    const busca = el("busca");
    const estado = el("estado");
    const lista = el("lista");

    const FILTROS = [
        ["todas", "Todas", () => true],
        ["saldo", "Com saldo", (v) => v.status === "FINALIZADA" && Number(v.saldo) > 0],
        ["atrasadas", "Atrasadas", (v) => Number(v.atrasado) > 0],
        ["quitadas", "Quitadas", (v) => v.status === "FINALIZADA" && Number(v.saldo) === 0],
        ["canceladas", "Canceladas", (v) => v.status === "CANCELADA"],
    ];

    let vendas = [];
    let filtro = new URLSearchParams(location.search).get("filtro") || "todas";
    /** O servidor publicado antes da busca nova não manda o atrasado nem as etiquetas. */
    let servidorNovo = true;

    function desenharFiltros() {
        el("filtros").replaceChildren(...FILTROS
            .filter(([id]) => servidorNovo || id !== "atrasadas")
            .map(([id, nome, regra]) => {
                const quantos = vendas.filter(regra).length;
                const b = criar("button", id === filtro ? "botao-pequeno" : "botao-secundario botao-pequeno", `${nome} (${quantos})`);
                b.type = "button";
                b.setAttribute("aria-pressed", String(id === filtro));
                b.addEventListener("click", () => {
                    filtro = id;
                    const url = new URL(location.href);
                    if (id === "todas") url.searchParams.delete("filtro"); else url.searchParams.set("filtro", id);
                    history.replaceState(null, "", url);
                    desenhar();
                });
                return b;
            }));
    }

    function desenhar() {
        desenharFiltros();
        const regra = (FILTROS.find(([id]) => id === filtro) || FILTROS[0])[2];
        const visiveis = vendas.filter(regra);
        lista.replaceChildren(...visiveis.map((v) => window.ItsJoiasVendas.cartao(v, { mostrarCliente: true, aoMudar: carregar })));
        estado.hidden = visiveis.length > 0;
        estado.className = "vazio";
        estado.textContent = busca.value.trim() ? "Nenhuma venda encontrada para esta busca."
            : filtro === "todas" ? "Nenhuma venda registrada ainda." : "Nenhuma venda neste filtro.";
    }

    let pedidoAtual = 0;

    async function carregar() {
        const meu = ++pedidoAtual;
        const termo = busca.value.trim();
        try {
            let resposta = await api("/api/vendas?limite=200" + (termo ? "&busca=" + encodeURIComponent(termo) : ""));
            if (meu !== pedidoAtual) return;
            servidorNovo = resposta.length === 0 || resposta[0].etiquetas !== undefined;
            if (!servidorNovo && termo) {
                // servidor antigo ignora a busca: procura aqui pelo nome da cliente ou pelo número
                const t = termo.toLowerCase().replace(/^#/, "");
                resposta = resposta.filter((v) => v.clienteNome.toLowerCase().includes(t) || String(v.vendaId) === t);
            }
            vendas = resposta;
            desenhar();
        } catch (erro) {
            if (meu !== pedidoAtual) return;
            lista.replaceChildren();
            estado.hidden = false;
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as vendas. " + erro.message;
        }
    }

    let espera;
    busca.addEventListener("input", () => { clearTimeout(espera); espera = setTimeout(carregar, 300); });

    carregar();
})();
