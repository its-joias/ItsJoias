// Maletas ativas, direto do banco (GET /api/maletas).
(function () {
    const { api, formato, criar, perguntar, notificar } = window.ItsJoias;
    const estado = document.getElementById("estado");
    const tabela = document.getElementById("tabela");

    async function carregar() {
        try {
            const maletas = await api("/api/maletas");
            document.getElementById("lista").replaceChildren(...maletas.map((m) => {
                const tr = criar("tr");
                const nome = criar("td");
                nome.append(criar("strong", null, m.nome), criar("div", "texto-suave", m.codigo));
                tr.append(nome, criar("td", null, m.fornecedor), criar("td", null, formato.data(m.dataEntrada)),
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

    carregar();
    window.ItsJoiasMaletas = { recarregar: carregar };
})();
