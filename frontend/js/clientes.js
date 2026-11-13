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
        const editar = criar("button", "botao-secundario botao-pequeno", "Editar");
        editar.type = "button";
        editar.addEventListener("click", () => abrirForm(c));
        const apagar = criar("button", "botao-perigo botao-pequeno", "Excluir");
        apagar.type = "button";
        apagar.dataset.somente = "PROPRIETARIA";
        apagar.addEventListener("click", () => excluir(c));
        acoes.append(editar, apagar);

        tr.append(nome, criar("td", null, c.telefone),
            criar("td", "numero", String(c.quantidadeVendas)),
            criar("td", "numero", formato.reais(c.totalEmAberto)), atrasado, acoes);
        return tr;
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
