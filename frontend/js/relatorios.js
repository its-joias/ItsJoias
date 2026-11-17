// Relatórios e Configurações. Os gráficos são os mesmos do Painel e de Produtos (painel-graficos.js e
// produtos-graficos.js desenham nos ids desta página). Aqui: os quatro números do topo (GET /api/painel/),
// o tema, a conta (GET /api/auth/eu) e o botão Sair.
(function () {
    const { api, formato, criar, sair, lerSessao } = window.ItsJoias;
    const el = (id) => document.getElementById(id);

    function indicador(rotulo, valor, nota, tom) {
        const d = criar("div", "indicador" + (tom ? " indicador--" + tom : ""));
        d.append(criar("span", "indicador-rotulo", rotulo), criar("strong", "indicador-valor", valor));
        if (nota) d.append(criar("span", "indicador-nota", nota));
        return d;
    }

    async function numeros() {
        const caixa = el("rel-indicadores");
        try {
            const p = await api("/api/painel/");
            const t = p.totais;
            caixa.replaceChildren(
                indicador("Total vendido", formato.reais(t.totalVendido.valor),
                    formato.plural(t.totalVendido.quantidadeVendas, "venda finalizada", "vendas finalizadas"), "salmao"),
                indicador("Recebido", formato.reais(t.recebido.valor),
                    `${Number(t.recebido.percentualDoVendido).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% do vendido`, "verde"),
                indicador("A receber", formato.reais(t.aReceber.valor),
                    formato.plural(t.aReceber.quantidadeParcelas, "parcela em aberto", "parcelas em aberto"), "pessego"),
                indicador("Clientes devendo", String(t.clientesDevendo.quantidade),
                    formato.plural(t.clientesDevendo.comParcelaAtrasada, "com parcela atrasada", "com parcela atrasada"), "areia"));
        } catch (erro) {
            caixa.replaceChildren(criar("p", "mensagem mensagem--erro", "Não foi possível carregar os números. " + erro.message));
        }
    }

    // ---- configurações ----------------------------------------------------------------

    const NOMES_PERFIL = { PROPRIETARIA: "Proprietária", DESENVOLVIMENTO: "Desenvolvimento" };

    function marcarTema() {
        const atual = window.ItsJoiasTema.atual();
        document.querySelectorAll('input[name="tema"]').forEach((r) => { r.checked = r.value === atual; });
    }

    document.querySelectorAll('input[name="tema"]').forEach((r) => {
        r.addEventListener("change", () => {
            if (r.checked && r.value !== window.ItsJoiasTema.atual()) window.ItsJoiasTema.alternar(r);
        });
    });
    document.addEventListener("itsjoias:tema", marcarTema);
    marcarTema();

    function conta(u) {
        el("conta-dados").replaceChildren(...[["Nome", u.nome], ["E-mail", u.email], ["Perfil", NOMES_PERFIL[u.perfil] || u.perfil]]
            .map(([rotulo, valor]) => {
                const linha = criar("div");
                linha.append(criar("dt", null, rotulo), criar("dd", null, valor || "—"));
                return linha;
            }));
    }

    async function carregarConta() {
        const sessao = lerSessao();
        if (sessao && sessao.usuario) conta(sessao.usuario);
        try {
            conta(await api("/api/auth/eu"));
            el("sistema-servidor").textContent = "No ar";
        } catch (erro) {
            el("sistema-servidor").textContent = "Sem resposta agora. " + erro.message;
        }
    }

    el("conta-sair").addEventListener("click", () => sair());

    window.ItsJoiasAbas.ligar(document.querySelector(".abas"));
    numeros();
    carregarConta();
})();
