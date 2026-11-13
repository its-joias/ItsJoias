// Clientes: lista vinda do banco, cadastro, edição, desativação e exclusão.
(function () {
    const { api, formato, criar, perguntar, notificar, modal } = window.ItsJoias;
    const mascaras = window.ItsJoiasMascaras;

    const el = (id) => document.getElementById(id);
    const secaoForm = el("formulario");
    const form = el("form-cliente");
    const tituloForm = el("titulo-form");
    const botaoSalvar = el("botao-salvar");
    const mensagem = el("form-mensagem");
    const lista = el("lista");
    const tabela = el("tabela");
    const estado = el("lista-estado");
    const busca = el("busca");
    const mostrarInativas = el("mostrar-inativas");
    const CAMPOS = ["nome", "telefone", "cpf", "email", "endereco", "observacoes"];

    let editandoId = null;

    mascaras.aplicar(el("telefone"), mascaras.telefone);
    mascaras.aplicar(el("cpf"), mascaras.cpf);

    // ---- mensagens ----------------------------------------------------------------------

    /** Erro dentro do modal (perto dos campos); sucesso e erros fora do modal viram aviso flutuante. */
    function erroNoFormulario(titulo, texto) {
        mensagem.className = "mensagem mensagem--erro";
        mensagem.replaceChildren(criar("strong", null, titulo));
        if (texto) mensagem.append(criar("p", null, texto));
        mensagem.hidden = false;
    }

    function limparErros() {
        form.querySelectorAll(".campo").forEach((c) => {
            c.classList.remove("com-erro");
            const e = c.querySelector(".erro-campo");
            if (e) e.textContent = "";
        });
    }

    function mostrarErrosCampos(campos) {
        Object.entries(campos || {}).forEach(([nome, texto]) => {
            const campo = form.querySelector(`[data-campo="${nome}"]`);
            if (!campo) return;
            campo.classList.add("com-erro");
            campo.querySelector(".erro-campo").textContent = texto;
        });
        const primeiro = form.querySelector(".com-erro input, .com-erro textarea");
        if (primeiro) primeiro.focus();
    }

    // ---- formulário ---------------------------------------------------------------------

    function abrirForm(cliente) {
        editandoId = cliente ? cliente.id : null;
        form.reset();
        limparErros();
        tituloForm.textContent = cliente ? "Editar cliente" : "Nova cliente";
        botaoSalvar.textContent = cliente ? "Salvar alterações" : "Salvar cliente";
        el("campo-ativa").hidden = !cliente;
        if (cliente) {
            CAMPOS.forEach((c) => { el(c).value = cliente[c] || ""; });
            el("ativa").checked = cliente.ativo;
        }
        mensagem.hidden = true;
        modal.abrir(secaoForm);
        el("nome").focus();
    }

    secaoForm.addEventListener("close", () => { editandoId = null; });

    el("botao-nova").addEventListener("click", () => abrirForm(null));

    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        limparErros();
        const dados = {};
        CAMPOS.forEach((c) => { dados[c] = el(c).value.trim() || null; });
        if (editandoId) dados.ativo = el("ativa").checked;

        const editando = editandoId;
        botaoSalvar.disabled = true;
        botaoSalvar.classList.add("processando");
        try {
            const salva = editando
                ? await api(`/api/clientes/${editando}`, { metodo: "PUT", corpo: dados })
                : await api("/api/clientes", { metodo: "POST", corpo: dados });
            modal.fechar(secaoForm);
            notificar({ tipo: "sucesso", titulo: editando ? "Alterações salvas." : "Cliente cadastrada.", texto: salva.nome });
            await carregar();
        } catch (erro) {
            mostrarErrosCampos(erro.campos);
            erroNoFormulario("Não foi possível salvar.", erro.message);
            mensagem.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } finally {
            botaoSalvar.disabled = false;
            botaoSalvar.classList.remove("processando");
        }
    });

    // ---- lista --------------------------------------------------------------------------

    async function excluir(cliente) {
        if (!await perguntar({
            tipo: "perigo", titulo: `Excluir ${cliente.nome}?`,
            texto: "A cliente sai do cadastro. Esta ação não pode ser desfeita.",
            detalhes: [["Telefone", cliente.telefone], ["Compras", String(cliente.quantidadeVendas)]],
            sim: "Excluir cliente", nao: "Manter",
        })) return;
        try {
            await api(`/api/clientes/${cliente.id}`, { metodo: "DELETE" });
            notificar({ tipo: "sucesso", titulo: "Cliente excluída.", texto: cliente.nome });
            await carregar();
        } catch (erro) {
            notificar({ tipo: "erro", titulo: "Não foi possível excluir.", texto: erro.message });
        }
    }

    function linha(c) {
        const tr = criar("tr");
        const nome = criar("td");
        nome.append(criar("strong", null, c.nome));
        if (!c.ativo) nome.append(" ", criar("span", "etiqueta-situacao", "Desativada"));
        if (c.email) nome.append(criar("div", "texto-suave", c.email));

        const atrasado = criar("td", "numero" + (Number(c.totalAtrasado) > 0 ? " texto-alerta" : ""),
            formato.reais(c.totalAtrasado));
        if (c.maiorAtrasoDias > 0) atrasado.title = `Maior atraso: ${formato.plural(c.maiorAtrasoDias, "dia", "dias")}`;

        const acoes = criar("td", "acoes-linha");
        const ver = criar("button", "botao-pequeno", "Ver");
        ver.type = "button";
        ver.setAttribute("aria-label", `Ver perfil e vendas de ${c.nome}`);
        ver.addEventListener("click", () => abrirPerfil(c));
        const editar = criar("button", "botao-secundario botao-pequeno", "Editar");
        editar.type = "button";
        editar.addEventListener("click", () => abrirForm(c));
        const apagar = criar("button", "botao-perigo botao-pequeno", "Excluir");
        apagar.type = "button";
        apagar.dataset.somente = "PROPRIETARIA";
        apagar.addEventListener("click", () => excluir(c));
        acoes.append(ver, editar, apagar);

        tr.append(nome, criar("td", null, c.telefone),
            criar("td", "numero", String(c.quantidadeVendas)),
            criar("td", "numero", formato.reais(c.totalEmAberto)), atrasado, acoes);
        return tr;
    }

    // ---- perfil da cliente: histórico de vendas e cancelamento ------------------------------

    const perfil = el("perfil");
    const SITUACAO = { FINALIZADA: "Finalizada", CANCELADA: "Cancelada" };
    const FORMAS = { PIX: "Pix", DINHEIRO: "Dinheiro", CARTAO: "Cartão", TRANSFERENCIA: "Transferência", OUTRO: "Outro" };
    let clienteDoPerfil = null;

    function item(rotulo, valor, destaque) {
        const d = criar("div", "resumo-item" + (destaque ? " resumo-item--erro" : ""));
        d.append(criar("span", "texto-suave", rotulo), criar("strong", null, valor));
        return d;
    }

    function abrirPerfil(c) {
        clienteDoPerfil = c;
        el("perfil-nome").textContent = c.nome;
        el("perfil-contato").textContent = [c.telefone, c.email].filter(Boolean).join(" · ");
        el("perfil-totais").replaceChildren(
            item("Compras", String(c.quantidadeVendas)),
            item("Em aberto", formato.reais(c.totalEmAberto)),
            item("Atrasado", formato.reais(c.totalAtrasado), Number(c.totalAtrasado) > 0));
        el("perfil-nova-venda").href = "vendas.html?cliente=" + c.id;
        el("perfil-nova-venda").hidden = !c.ativo;
        modal.abrir(perfil);
        carregarVendas();
    }

    async function carregarVendas() {
        const estado = el("perfil-estado");
        const lista = el("perfil-vendas");
        estado.className = "carregando-texto";
        estado.textContent = "Carregando vendas…";
        estado.hidden = false;
        lista.replaceChildren();
        try {
            const vendasDaCliente = await api("/api/vendas?limite=200&clienteId=" + clienteDoPerfil.id);
            lista.replaceChildren(...vendasDaCliente.map(cartaoVenda));
            estado.hidden = vendasDaCliente.length > 0;
            estado.className = "vazio";
            estado.textContent = "Esta cliente ainda não tem vendas.";
        } catch (erro) {
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as vendas. " + erro.message;
        }
    }

    function cartaoVenda(v) {
        const li = criar("li", "venda-cartao" + (v.status === "CANCELADA" ? " venda-cartao--cancelada" : ""));
        const topo = criar("button", "venda-cartao-topo");
        topo.type = "button";
        topo.setAttribute("aria-expanded", "false");
        const esquerda = criar("span", "venda-cartao-titulo");
        esquerda.append(criar("strong", null, `Venda #${v.vendaId}`), criar("span", "texto-suave", ` · ${formato.data(v.dataVenda)} · `
            + formato.plural(v.quantidadePecas, "peça", "peças")),
            criar("span", "etiqueta-situacao etiqueta-" + v.status.toLowerCase(), SITUACAO[v.status] || v.status));
        const direita = criar("span", "venda-cartao-valores");
        direita.append(criar("strong", "dinheiro", formato.reais(v.valorTotal)));
        if (v.status === "FINALIZADA") {
            direita.append(criar("span", "texto-suave", Number(v.saldo) > 0 ? `falta ${formato.reais(v.saldo)}` : "quitada"));
        }
        topo.append(esquerda, direita);
        const corpo = criar("div", "venda-cartao-corpo");
        corpo.hidden = true;
        topo.addEventListener("click", async () => {
            const abrir = corpo.hidden;
            corpo.hidden = !abrir;
            topo.setAttribute("aria-expanded", String(abrir));
            if (abrir && !corpo.dataset.carregado) await detalharVenda(v.vendaId, corpo);
        });
        li.append(topo, corpo);
        return li;
    }

    async function detalharVenda(vendaId, corpo) {
        corpo.replaceChildren(criar("p", "carregando-texto", "Carregando detalhes…"));
        try {
            const d = await api("/api/vendas/" + vendaId);
            corpo.dataset.carregado = "1";
            const blocos = [];
            if (d.status === "CANCELADA") {
                const aviso = criar("div", "mensagem mensagem--alerta");
                aviso.append(criar("strong", null, "Venda cancelada em " + new Date(d.canceladaEm).toLocaleString("pt-BR",
                    { dateStyle: "short", timeStyle: "short" })), criar("p", null, "Motivo: " + d.motivoCancelamento));
                blocos.push(aviso);
            }
            const pecas = criar("ul", "venda-lista");
            d.itens.forEach((i) => {
                const l = criar("li");
                l.append(criar("span", null, `${i.etiqueta} · ${i.produto}`), criar("span", "texto-suave", i.maleta),
                    criar("span", "dinheiro", formato.reais(i.preco)));
                pecas.append(l);
            });
            blocos.push(criar("h5", null, "Peças"), pecas);
            if (Number(d.acrescimo) > 0) blocos.push(criar("p", "texto-suave", "Acréscimo de 10%: " + formato.reais(d.acrescimo)));
            const parcelas = criar("ul", "venda-lista");
            d.parcelas.forEach((p) => {
                const l = criar("li");
                const situacao = p.status === "CANCELADA" ? "cancelada" : Number(p.pago) >= Number(p.valor) ? "paga"
                    : Number(p.pago) > 0 ? `pago ${formato.reais(p.pago)}` : "em aberto";
                l.append(criar("span", null, `${p.numero}ª · vence ${formato.data(p.vencimento)}`),
                    criar("span", "texto-suave", situacao), criar("span", "dinheiro", formato.reais(p.valor)));
                parcelas.append(l);
            });
            blocos.push(criar("h5", null, "Parcelas"), parcelas);
            if (d.pagamentos.length) {
                const pagamentos = criar("ul", "venda-lista");
                d.pagamentos.forEach((p) => {
                    const l = criar("li", p.estornado ? "estornado" : null);
                    l.append(criar("span", null, `${formato.data(p.data)} · ${FORMAS[p.forma] || p.forma}`),
                        criar("span", "texto-suave", p.estornado ? "estornado" : "recebido"), criar("span", "dinheiro", formato.reais(p.valor)));
                    pagamentos.append(l);
                });
                blocos.push(criar("h5", null, "Pagamentos"), pagamentos);
            }
            blocos.push(criar("p", "texto-suave", "Registrada por " + (d.vendidaPor || "—") + "."));
            if (d.status === "FINALIZADA") {
                const cancelar = criar("button", "botao-perigo botao-pequeno", "Cancelar venda");
                cancelar.type = "button";
                cancelar.dataset.somente = "PROPRIETARIA";
                cancelar.addEventListener("click", () => cancelarVenda(d));
                const acoesVenda = criar("div", "acoes");
                acoesVenda.append(cancelar);
                blocos.push(acoesVenda);
            }
            corpo.replaceChildren(...blocos);
        } catch (erro) {
            corpo.replaceChildren(criar("p", "mensagem mensagem--erro", "Não foi possível carregar a venda. " + erro.message));
        }
    }

    async function cancelarVenda(d) {
        const recebido = d.pagamentos.filter((p) => !p.estornado).reduce((s, p) => s + Number(p.valor), 0);
        const motivo = await perguntar({
            tipo: "perigo",
            titulo: `Cancelar a venda #${d.vendaId}?`,
            texto: ["As peças voltam a ficar disponíveis na maleta de onde saíram e as parcelas deixam de ser cobradas.",
                "A venda não é apagada: fica no histórico como cancelada, com o motivo."],
            detalhes: [["Cliente", d.clienteNome], ["Total", formato.reais(d.valorTotal)],
                ["Peças", String(d.itens.length)], ["Pagamentos estornados", formato.reais(recebido)]],
            campo: { rotulo: "Motivo do cancelamento", placeholder: "Ex.: lançada para a cliente errada", minimo: 3 },
            sim: "Cancelar venda", nao: "Manter venda",
        });
        if (!motivo) return;
        try {
            await api(`/api/vendas/${d.vendaId}/cancelar`, { metodo: "POST", corpo: { motivo } });
            notificar({ tipo: "sucesso", titulo: `Venda #${d.vendaId} cancelada.`, texto: "As peças voltaram para a maleta." });
            await carregar();
            const atualizada = (await api("/api/clientes?incluirInativos=true")).find((c) => c.id === clienteDoPerfil.id);
            if (atualizada) abrirPerfilAtualizado(atualizada);
        } catch (erro) {
            notificar({ tipo: "erro", titulo: "Não foi possível cancelar.", texto: erro.message });
        }
    }

    /** Recarrega o perfil aberto (totais e vendas) sem fechar o modal. */
    function abrirPerfilAtualizado(c) {
        clienteDoPerfil = c;
        el("perfil-totais").replaceChildren(
            item("Compras", String(c.quantidadeVendas)),
            item("Em aberto", formato.reais(c.totalEmAberto)),
            item("Atrasado", formato.reais(c.totalAtrasado), Number(c.totalAtrasado) > 0));
        carregarVendas();
    }

    let pedidoAtual = 0;

    async function carregar() {
        const meu = ++pedidoAtual;
        const params = new URLSearchParams();
        if (busca.value.trim()) params.set("busca", busca.value.trim());
        if (mostrarInativas.checked) params.set("incluirInativos", "true");
        try {
            const clientes = await api("/api/clientes?" + params);
            if (meu !== pedidoAtual) return; // chegou uma resposta mais nova
            lista.replaceChildren(...clientes.map(linha));
            tabela.hidden = clientes.length === 0;
            estado.hidden = clientes.length > 0;
            estado.className = "vazio";
            estado.textContent = busca.value.trim()
                ? "Nenhuma cliente encontrada para esta busca."
                : "Nenhuma cliente cadastrada ainda. Use o botão “+ Nova cliente”.";
        } catch (erro) {
            if (meu !== pedidoAtual) return;
            tabela.hidden = true;
            estado.hidden = false;
            estado.className = "mensagem mensagem--erro";
            estado.textContent = "Não foi possível carregar as clientes. " + erro.message;
        }
    }

    let espera;
    busca.addEventListener("input", () => { clearTimeout(espera); espera = setTimeout(carregar, 300); });
    mostrarInativas.addEventListener("change", carregar);

    carregar();
})();
