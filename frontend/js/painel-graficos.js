// Gráficos do Painel: vendas por mês e recebido × a receber.
(function () {
    const { api, criar } = window.ItsJoias;
    const g = window.ItsJoiasGraficos;

    function erro(caixa, mensagem) {
        caixa.replaceChildren(criar("p", "mensagem mensagem--erro", "Não foi possível carregar o gráfico. " + mensagem));
    }

    async function vendas() {
        const caixa = document.getElementById("grafico-vendas");
        try {
            const meses = await api("/api/graficos/vendas-por-mes");
            const valores = meses.map((m) => Number(m.vendido));
            const maior = valores.indexOf(Math.max(...valores));
            g.colunas(caixa, {
                titulo: "Total vendido por mês, últimos 12 meses",
                rotuloCategoria: "Mês",
                categorias: meses.map((m) => g.mes(m.mes)),
                series: [{ nome: "Vendido", cor: "var(--barra-vendidas)", valores }],
                destacar: [maior, valores.length - 1], // o maior mês e o mês atual
                extraBalao: (i) => [meses[i].vendas === 1 ? "1 venda" : `${meses[i].vendas} vendas`],
            });
        } catch (e) {
            erro(caixa, e.message);
        }
    }

    async function recebimentos() {
        const caixa = document.getElementById("grafico-recebimentos");
        try {
            const meses = await api("/api/graficos/recebimentos-por-mes");
            g.colunas(caixa, {
                titulo: "Parcelas por mês de vencimento: recebido e a receber",
                rotuloCategoria: "Vencimento",
                categorias: meses.map((m) => g.mes(m.mes)),
                series: [
                    { nome: "Recebido", cor: "var(--barra-vendidas)", valores: meses.map((m) => Number(m.recebido)) },
                    { nome: "A receber", cor: "var(--barra-disponiveis)", valores: meses.map((m) => Number(m.aReceber)) },
                ],
                extraBalao: (i) => (Number(meses[i].atrasado) > 0 ? [`Atrasado: ${g.reais(Number(meses[i].atrasado))}`] : []),
            });
        } catch (e) {
            erro(caixa, e.message);
        }
    }

    vendas();
    recebimentos();
})();
