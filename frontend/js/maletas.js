// Maletas ativas, direto do banco (GET /api/maletas).
(function () {
    const { api, formato, criar } = window.ItsJoias;
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
                    criar("td", "numero", formato.reais(m.custoTotal)), criar("td", "numero", formato.reais(m.totalVendido)));
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

    carregar();
    window.ItsJoiasMaletas = { recarregar: carregar };
})();
