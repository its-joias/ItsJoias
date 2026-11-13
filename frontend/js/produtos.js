// Peças disponíveis, direto do banco (GET /api/pecas), com filtro por categoria.
(function () {
    const { api, formato, criar } = window.ItsJoias;
    const el = (id) => document.getElementById(id);
    const estado = el("estado");
    const tabela = el("tabela");
    const busca = el("busca");

    let pecas = [];
    let categoria = "";

    function desenharCategorias() {
        const nomes = [...new Set(pecas.map((p) => p.categoria))].sort();
        const botoes = ["", ...nomes].map((nome) => {
            const b = criar("button", nome === categoria ? "botao-pequeno" : "botao-secundario botao-pequeno", nome || "Todas");
            b.type = "button";
            b.setAttribute("aria-pressed", String(nome === categoria));
            b.addEventListener("click", () => { categoria = nome; desenhar(); });
            return b;
        });
        el("categorias").replaceChildren(...botoes);
    }

    function desenhar() {
        desenharCategorias();
        const visiveis = categoria ? pecas.filter((p) => p.categoria === categoria) : pecas;
        el("lista").replaceChildren(...visiveis.map((p) => {
            const tr = criar("tr");
            const etiqueta = criar("td");
            etiqueta.append(criar("strong", null, p.etiqueta));
            tr.append(etiqueta, criar("td", null, p.produto), criar("td", null, p.categoria), criar("td", null, p.maleta),
                criar("td", "numero", formato.reais(p.preco)));
            return tr;
        }));
        tabela.hidden = visiveis.length === 0;
        estado.hidden = visiveis.length > 0;
        estado.className = "vazio";
        estado.textContent = busca.value.trim() ? "Nenhuma peça disponível encontrada para esta busca."
            : "Nenhuma peça disponível. As peças aparecem aqui quando uma maleta dá entrada.";
    }

    let pedidoAtual = 0;

    async function carregar() {
        const meu = ++pedidoAtual;
        try {
            const resposta = await api("/api/pecas?limite=200&busca=" + encodeURIComponent(busca.value.trim()));
            if (meu !== pedidoAtual) return;
            pecas = resposta;
            desenhar();
        } catch (erro) {
            if (meu !== pedidoAtual) return;
            tabela.hidden = true;
            estado.hidden = false;
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as peças. " + erro.message;
        }
    }

    let espera;
    busca.addEventListener("input", () => { clearTimeout(espera); espera = setTimeout(carregar, 300); });

    carregar();
})();
