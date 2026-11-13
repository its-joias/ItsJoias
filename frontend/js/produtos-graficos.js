// Gráfico da tela de Produtos: peças por categoria (maletas ativas).
(function () {
    const { api, criar } = window.ItsJoias;
    const g = window.ItsJoiasGraficos;
    const caixa = document.getElementById("grafico-categorias");

    (async () => {
        try {
            const categorias = await api("/api/graficos/pecas-por-categoria");
            if (!categorias.length) {
                caixa.replaceChildren(criar("p", "vazio", "Nenhuma peça nas maletas ativas ainda."));
                return;
            }
            const pecas = (n) => (n === 1 ? "1 peça" : `${n} peças`);
            g.barras(caixa, {
                titulo: "Peças por categoria nas maletas ativas",
                rotuloCategoria: "Categoria",
                categorias: categorias.map((c) => c.categoria),
                series: [
                    { nome: "Disponíveis", cor: "var(--barra-disponiveis)", valores: categorias.map((c) => c.disponiveis) },
                    { nome: "Vendidas", cor: "var(--barra-vendidas)", valores: categorias.map((c) => c.vendidas) },
                    { nome: "Perdidas ou devolvidas", cor: "var(--barra-perdidas)", valores: categorias.map((c) => c.perdidasOuDevolvidas) },
                ],
                formatar: pecas,
            });
        } catch (e) {
            caixa.replaceChildren(criar("p", "mensagem mensagem--erro", "Não foi possível carregar o gráfico. " + e.message));
        }
    })();
})();
