// Ficha da cliente (cliente.html?id=3): quem é, os números (GET /api/clientes/{id}),
// o histórico de compras (GET /api/vendas?clienteId=), as parcelas em aberto (detalhe de cada venda com saldo,
// GET /api/vendas/{id}) e os dados cadastrais. Nenhuma conta de dinheiro: os valores vêm prontos da API.
(function () {
    const { api, formato, criar } = window.ItsJoias;
    const el = (id) => document.getElementById(id);
    const id = Number(new URLSearchParams(location.search).get("id"));

    let cliente = null;
    let vendas = [];
    let parcelasCarregadas = false;

    function iniciais(nome) {
        const partes = nome.trim().split(/\s+/);
        return (partes[0][0] + (partes.length > 1 ? partes[partes.length - 1][0] : "")).toUpperCase();
    }

    function indicador(rotulo, valor, nota, tom) {
        const d = criar("div", "indicador" + (tom ? " indicador--" + tom : ""));
        d.append(criar("span", "indicador-rotulo", rotulo), criar("strong", "indicador-valor", valor));
        if (nota) d.append(criar("span", "indicador-nota", nota));
        return d;
    }

    function desenharFicha() {
        document.title = `${cliente.nome} | Organização Its Joias`;
        el("ficha-nome").textContent = cliente.nome;
        el("ficha-avatar").textContent = iniciais(cliente.nome);
        el("ficha-contato").textContent = [cliente.telefone, cliente.email].filter(Boolean).join(" · ");

        const situacao = el("ficha-situacao");
        situacao.replaceChildren(criar("span", "pilula " + (cliente.ativo ? "pilula--verde" : "pilula--neutra"),
            cliente.ativo ? "Cliente ativa" : "Desativada"));
        if (Number(cliente.totalAtrasado) > 0) {
            situacao.append(criar("span", "pilula pilula--salmao",
                `Em atraso há ${formato.plural(cliente.maiorAtrasoDias, "dia", "dias")}`));
        }

        el("ficha-indicadores").replaceChildren(
            indicador("Compras", String(cliente.quantidadeVendas), null, "areia"),
            indicador("Total comprado", formato.reais(cliente.totalComprado), null, "pessego"),
            indicador("Em aberto", formato.reais(cliente.totalEmAberto), null, Number(cliente.totalEmAberto) > 0 ? "pessego" : "verde"),
            indicador("Atrasado", formato.reais(cliente.totalAtrasado),
                Number(cliente.totalAtrasado) > 0 ? "maior atraso: " + formato.plural(cliente.maiorAtrasoDias, "dia", "dias") : "em dia",
                Number(cliente.totalAtrasado) > 0 ? "salmao" : "verde"));

        const novaVenda = el("ficha-nova-venda");
        novaVenda.href = "vendas.html?cliente=" + cliente.id;
        novaVenda.hidden = !cliente.ativo;

        const dados = [["Nome", cliente.nome], ["Telefone", cliente.telefone], ["E-mail", cliente.email],
            ["CPF", cliente.cpf], ["Endereço", cliente.endereco], ["Observações", cliente.observacoes]];
        el("dados-lista").replaceChildren(...dados.map(([rotulo, valor]) => {
            const linha = criar("div");
            linha.append(criar("dt", null, rotulo), criar("dd", valor ? null : "texto-suave", valor || "Não informado"));
            return linha;
        }));
        el("dados-editar").href = "clientes.html?editar=" + cliente.id;
    }

    async function carregarCompras() {
        const estado = el("compras-estado");
        estado.hidden = false;
        estado.className = "carregando-texto";
        estado.textContent = "Carregando compras…";
        try {
            vendas = await api("/api/vendas?limite=200&clienteId=" + id);
            el("compras-lista").replaceChildren(...vendas.map((v) =>
                window.ItsJoiasVendas.cartao(v, { aoMudar: recarregarTudo })));
            estado.hidden = vendas.length > 0;
            estado.className = "vazio";
            estado.textContent = "Esta cliente ainda não tem compras.";
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as compras. " + erro.message;
        }
    }

    /** Parcelas ativas que ainda têm saldo, de todas as vendas da cliente com algo a pagar. */
    async function carregarParcelas() {
        const estado = el("parcelas-estado");
        const caixa = el("parcelas-caixa");
        estado.hidden = false;
        estado.className = "carregando-texto";
        estado.textContent = "Carregando parcelas…";
        caixa.hidden = true;
        try {
            const comSaldo = vendas.filter((v) => v.status !== "CANCELADA" && Number(v.saldo) > 0);
            const detalhes = await Promise.all(comSaldo.map((v) => api("/api/vendas/" + v.vendaId)));
            const hoje = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Sao_Paulo" });
            const linhas = [];
            detalhes.forEach((d, i) => {
                const venda = comSaldo[i];
                const total = d.parcelas.filter((p) => p.status === "ATIVA").length;
                d.parcelas.filter((p) => p.status === "ATIVA" && Number(p.pago) < Number(p.valor)).forEach((p) => {
                    const atrasada = p.vencimento < hoje;
                    const parcial = Number(p.pago) > 0;
                    const tr = criar("tr");
                    const situacao = criar("td");
                    situacao.append(criar("span", "pilula " + (atrasada ? "pilula--salmao" : "pilula--neutra"),
                        atrasada ? "Atrasada" : parcial ? "Parcial" : "Pendente"));
                    tr.append(criar("td", null, "#" + venda.vendaId), criar("td", null, `${p.numero}/${total}`),
                        criar("td", null, formato.data(p.vencimento)), criar("td", "numero", formato.reais(p.valor)),
                        criar("td", "numero", formato.reais(p.pago)), situacao);
                    linhas.push([p.vencimento, tr]);
                });
            });
            linhas.sort((a, b) => a[0].localeCompare(b[0]));
            el("parcelas-lista").replaceChildren(...linhas.map(([, tr]) => tr));
            caixa.hidden = linhas.length === 0;
            estado.hidden = linhas.length > 0;
            estado.className = "vazio";
            estado.textContent = "Nenhuma parcela em aberto. Tudo pago.";
            parcelasCarregadas = true;
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as parcelas. " + erro.message;
        }
    }

    async function recarregarTudo() {
        cliente = await api("/api/clientes/" + id);
        desenharFicha();
        await carregarCompras();
        parcelasCarregadas = false;
        if (!el("painel-parcelas").hidden) carregarParcelas();
    }

    async function iniciar() {
        const estado = el("ficha-estado");
        if (!id) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Cliente não informada. Volte para a lista de clientes e clique em Ver.";
            return;
        }
        try {
            cliente = await api("/api/clientes/" + id);
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível abrir esta cliente. " + erro.message;
            return;
        }
        estado.hidden = true;
        desenharFicha();
        el("ficha-abas").hidden = false;
        await carregarCompras();
        window.ItsJoiasAbas.ligar(el("ficha-abas"), (painel) => {
            if (painel === "painel-parcelas" && !parcelasCarregadas) carregarParcelas();
        });
    }

    iniciar();
})();
