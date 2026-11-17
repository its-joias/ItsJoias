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
                // na tabela, cada mês abre os dias dele com o total vendido em cada um
                dicaDetalhe: "Ver quanto foi vendido em cada dia do mês",
                detalhar: (i) => diasDoMes(meses[i].mes),
            });
        } catch (e) {
            erro(caixa, e.message);
        }
    }

    const SEMANA = ["dom", "seg", "ter", "qua", "qui", "sex", "sáb"];

    /** "2026-11-03" -> "03/11 · ter" (sem new Date() no fuso local, que trocaria o dia) */
    function rotuloDoDia(iso) {
        const [ano, mes, dia] = iso.split("-").map(Number);
        const semana = SEMANA[new Date(Date.UTC(ano, mes - 1, dia)).getUTCDay()];
        return `${String(dia).padStart(2, "0")}/${String(mes).padStart(2, "0")} · ${semana}`;
    }

    /** Os dias de um mês ("2026-11") com o total vendido em cada um (GET /api/graficos/vendas-por-dia). */
    async function diasDoMes(mes) {
        let dias;
        try {
            dias = await api("/api/graficos/vendas-por-dia?mes=" + mes);
        } catch (e) {
            if (e.status === 401 || e.status === 403 || e.status === 400) throw e;
            dias = await diasPelasVendas(mes); // servidor de antes do detalhe por dia
        }
        return dias.map((d) => ({ rotulo: rotuloDoDia(d.dia), valores: [Number(d.vendido)], vazio: Number(d.vendido) === 0 }));
    }

    /** Sem o endpoint por dia: soma as vendas finalizadas do mês pela lista de vendas (as 200 mais recentes). */
    async function diasPelasVendas(mes) {
        const vendas = await api("/api/vendas?limite=200");
        const [ano, m] = mes.split("-").map(Number);
        const total = new Date(Date.UTC(ano, m, 0)).getUTCDate();
        const dias = Array.from({ length: total }, (_, k) => ({ dia: `${mes}-${String(k + 1).padStart(2, "0")}`, vendido: 0 }));
        vendas.filter((v) => v.status === "FINALIZADA" && v.dataVenda.startsWith(mes))
            .forEach((v) => { dias[Number(v.dataVenda.slice(8, 10)) - 1].vendido += Number(v.valorTotal); });
        return dias;
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

    /** Pizza: as 3 categorias que mais venderam + "Outras" (mais fatias ficariam difíceis de distinguir). */
    async function pizza() {
        const caixa = document.getElementById("grafico-pizza");
        try {
            const categorias = await api("/api/graficos/vendido-por-categoria");
            if (!categorias.length) {
                caixa.replaceChildren(criar("p", "vazio", "Nenhuma venda nos últimos 12 meses."));
                return;
            }
            const cores = ["var(--barra-vendidas)", "var(--barra-disponiveis)", "var(--barra-perdidas)"];
            const pecas = (n) => (n === 1 ? "1 peça" : `${n} peças`);
            const fatias = categorias.slice(0, 3).map((c, i) => ({ nome: c.categoria, valor: Number(c.vendido), cor: cores[i],
                detalhe: pecas(c.pecas) }));
            const resto = categorias.slice(3);
            if (resto.length) {
                fatias.push({ nome: resto.length === 1 ? resto[0].categoria : "Outras", cor: "var(--fatia-outras)",
                    valor: resto.reduce((s, c) => s + Number(c.vendido), 0),
                    detalhe: resto.map((c) => c.categoria).join(", ") });
            }
            g.pizza(caixa, { titulo: "Vendido por categoria, últimos 12 meses", rotuloCategoria: "Categoria", fatias });
        } catch (e) {
            erro(caixa, e.message);
        }
    }

    vendas();
    pizza();
    recebimentos();
})();
