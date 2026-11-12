// Clientes: lista vinda do banco, cadastro, edição, desativação e exclusão.
(function () {
    const { api, formato, criar, perguntar } = window.ItsJoias;
    const mascaras = window.ItsJoiasMascaras;

    const el = (id) => document.getElementById(id);
    const secaoForm = el("formulario");
    const form = el("form-cliente");
    const tituloForm = el("titulo-form");
    const botaoSalvar = el("botao-salvar");
    const mensagem = el("mensagem");
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

    function avisar(tipo, titulo, texto) {
        mensagem.className = "mensagem mensagem--" + tipo;
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
        secaoForm.hidden = false;
        secaoForm.scrollIntoView({ behavior: "smooth", block: "start" });
        el("nome").focus({ preventScroll: true });
    }

    function fecharForm() {
        secaoForm.hidden = true;
        editandoId = null;
    }

    el("botao-nova").addEventListener("click", () => { mensagem.hidden = true; abrirForm(null); });
    el("botao-cancelar").addEventListener("click", fecharForm);

    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        limparErros();
        const dados = {};
        CAMPOS.forEach((c) => { dados[c] = el(c).value.trim() || null; });
        if (editandoId) dados.ativo = el("ativa").checked;

        botaoSalvar.disabled = true;
        try {
            const salva = editandoId
                ? await api(`/api/clientes/${editandoId}`, { metodo: "PUT", corpo: dados })
                : await api("/api/clientes", { metodo: "POST", corpo: dados });
            fecharForm();
            avisar("sucesso", editandoId ? "Alterações salvas." : "Cliente cadastrada.", salva.nome);
            await carregar();
        } catch (erro) {
            mostrarErrosCampos(erro.campos);
            avisar("erro", "Não foi possível salvar.", erro.message);
            mensagem.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } finally {
            botaoSalvar.disabled = false;
        }
    });

    // ---- lista --------------------------------------------------------------------------

    async function excluir(cliente) {
        if (!await perguntar({ titulo: `Excluir ${cliente.nome}?`, texto: "Esta ação não pode ser desfeita.",
            sim: "Excluir", perigo: true })) return;
        try {
            await api(`/api/clientes/${cliente.id}`, { metodo: "DELETE" });
            avisar("sucesso", "Cliente excluída.", cliente.nome);
            await carregar();
        } catch (erro) {
            avisar("erro", "Não foi possível excluir.", erro.message);
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
        editar.addEventListener("click", () => { mensagem.hidden = true; abrirForm(c); });
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
