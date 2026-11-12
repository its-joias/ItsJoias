// Componentes de interface do Its Joias, iguais em todas as telas (no lugar de alert/confirm/prompt do navegador):
//   ItsJoias.perguntar({ tipo, titulo, texto, detalhes, sim, nao })  -> Promise<boolean>   janela de confirmação
//   ItsJoias.notificar({ tipo, titulo, texto, duracao })                                   aviso flutuante (canto da tela)
//   ItsJoias.modal.abrir(dialog) / ItsJoias.modal.fechar(dialog)                            modal de edição/visualização
//   ItsJoias.icone(nome)                                                                     ícone SVG da identidade
//   ItsJoias.botaoTema()                                                                     botão sol/lua do cabeçalho
// Tipos: "sucesso", "erro", "alerta", "info", "perigo" (exclusão), "importar", "confirmar".
(function () {
    const { criar } = window.ItsJoias;
    const SVG = "http://www.w3.org/2000/svg";

    // Ícones em traço (herdam a cor do texto). Desenhos simples, 24x24.
    const DESENHOS = {
        sucesso: ["M20 6 9 17l-5-5"],
        erro: ["M18 6 6 18", "M6 6l12 12"],
        alerta: ["M12 3 2 20h20L12 3z", "M12 10v4", "M12 17.5v.01"],
        info: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 11v6", "M12 7.5v.01"],
        confirmar: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2.5-3 4.5", "M12 17.5v.01"],
        perigo: ["M3 6h18", "M8 6V4h8v2", "M6 6l1 14h10l1-14", "M10 11v6", "M14 11v6"],
        importar: ["M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z", "M14 3v5h5", "M12 11v6", "M9 14l3 3 3-3"],
        fechar: ["M18 6 6 18", "M6 6l12 12"],
        sol: ["M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z", "M12 1v2", "M12 21v2", "M4.2 4.2l1.4 1.4", "M18.4 18.4l1.4 1.4",
            "M1 12h2", "M21 12h2", "M4.2 19.8l1.4-1.4", "M18.4 5.6l1.4-1.4"],
        lua: ["M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"],
    };

    function icone(nome) {
        const svg = document.createElementNS(SVG, "svg");
        svg.setAttribute("viewBox", "0 0 24 24");
        svg.setAttribute("aria-hidden", "true");
        svg.setAttribute("class", "icone");
        (DESENHOS[nome] || DESENHOS.info).forEach((d) => {
            const p = document.createElementNS(SVG, "path");
            p.setAttribute("d", d);
            svg.append(p);
        });
        return svg;
    }

    const semAnimacao = () => window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

    // ---- modal (base de todas as janelas) ------------------------------------------------

    /** Fecha com uma animação curta de saída; o evento "close" do <dialog> continua valendo. */
    function fechar(dialog) {
        if (!dialog.open || dialog.classList.contains("saindo")) return;
        if (semAnimacao()) {
            dialog.close();
            return;
        }
        dialog.classList.add("saindo");
        setTimeout(() => {
            dialog.classList.remove("saindo");
            dialog.close();
        }, 160);
    }

    function abrir(dialog) {
        if (dialog.open) return;
        if (!dialog.dataset.preparado) {
            dialog.dataset.preparado = "1";
            // Esc anima a saída em vez de fechar seco
            dialog.addEventListener("cancel", (e) => {
                e.preventDefault();
                fechar(dialog);
            });
            // clique no fundo escurecido fecha (só em modais que permitem)
            dialog.addEventListener("click", (e) => {
                if (e.target === dialog && dialog.dataset.fecharNoFundo !== "nao") fechar(dialog);
            });
            dialog.querySelectorAll("[data-fechar-modal]").forEach((b) => b.addEventListener("click", () => fechar(dialog)));
        }
        dialog.showModal();
    }

    // ---- confirmação ---------------------------------------------------------------------

    /**
     * opcoes: { tipo: "confirmar" | "perigo" | "importar" | "alerta", titulo, texto (string ou lista),
     *           detalhes: [[rótulo, valor], ...], sim: "Confirmar", nao: "Voltar" }
     */
    function perguntar(opcoes) {
        const tipo = opcoes.tipo || (opcoes.perigo ? "perigo" : "confirmar");
        return new Promise((resolver) => {
            const janela = criar("dialog", `janela janela--${tipo}`);
            janela.dataset.fecharNoFundo = "nao";
            const idTitulo = "janela-titulo-" + Math.random().toString(36).slice(2, 8);
            janela.setAttribute("aria-labelledby", idTitulo);

            const selo = criar("div", "janela-selo");
            selo.append(icone(tipo));
            const titulo = criar("h3", null, opcoes.titulo);
            titulo.id = idTitulo;
            janela.append(selo, titulo);
            (Array.isArray(opcoes.texto) ? opcoes.texto : [opcoes.texto]).filter(Boolean)
                .forEach((t) => janela.append(criar("p", "janela-texto", t)));
            if (opcoes.detalhes && opcoes.detalhes.length) {
                const dl = criar("dl", "janela-detalhes");
                opcoes.detalhes.forEach(([rotulo, valor]) => dl.append(criar("dt", null, rotulo), criar("dd", null, valor)));
                janela.append(dl);
            }

            const acoes = criar("div", "janela-acoes");
            const nao = criar("button", "botao-secundario", opcoes.nao || "Voltar");
            const sim = criar("button", tipo === "perigo" ? "botao-perigo-cheio" : null, opcoes.sim || "Confirmar");
            nao.type = sim.type = "button";
            acoes.append(nao, sim);
            janela.append(acoes);

            let resposta = false;
            sim.addEventListener("click", () => { resposta = true; fechar(janela); });
            nao.addEventListener("click", () => fechar(janela));
            janela.addEventListener("close", () => { janela.remove(); resolver(resposta); });
            document.body.append(janela);
            abrir(janela);
            (tipo === "perigo" ? nao : sim).focus(); // em exclusão, o foco começa no botão seguro
        });
    }

    // ---- notificações flutuantes ---------------------------------------------------------

    let area = null;

    function areaNotificacoes() {
        if (!area) {
            area = criar("div", "notificacoes");
            area.setAttribute("aria-live", "polite");
            document.body.append(area);
        }
        return area;
    }

    /** opcoes: { tipo: "sucesso" | "erro" | "alerta" | "info", titulo, texto, duracao (ms; 0 = fica até fechar) } */
    function notificar(opcoes) {
        const tipo = opcoes.tipo || "info";
        const caixa = criar("div", `notificacao notificacao--${tipo}`);
        caixa.setAttribute("role", tipo === "erro" ? "alert" : "status");
        const selo = criar("span", "notificacao-selo");
        selo.append(icone(tipo));
        const corpo = criar("div", "notificacao-corpo");
        corpo.append(criar("strong", null, opcoes.titulo));
        if (opcoes.texto) corpo.append(criar("p", null, opcoes.texto));
        const x = criar("button", "notificacao-fechar");
        x.type = "button";
        x.setAttribute("aria-label", "Fechar aviso");
        x.append(icone("fechar"));
        caixa.append(selo, corpo, x);

        const sair = () => {
            if (caixa.classList.contains("saindo")) return;
            caixa.classList.add("saindo");
            setTimeout(() => caixa.remove(), semAnimacao() ? 0 : 200);
        };
        x.addEventListener("click", sair);
        const duracao = opcoes.duracao ?? (tipo === "erro" ? 0 : tipo === "alerta" ? 9000 : 5000);
        if (duracao > 0) {
            let tempo = setTimeout(sair, duracao);
            // passar o mouse ou focar segura o aviso na tela
            caixa.addEventListener("mouseenter", () => clearTimeout(tempo));
            caixa.addEventListener("mouseleave", () => { tempo = setTimeout(sair, 2500); });
        }
        const lugar = areaNotificacoes();
        lugar.append(caixa);
        while (lugar.children.length > 4) lugar.firstElementChild.remove();
        return { fechar: sair };
    }

    // ---- tema ----------------------------------------------------------------------------

    function botaoTema() {
        const b = criar("button", "botao-tema");
        b.type = "button";
        b.setAttribute("role", "switch");
        const sol = criar("span", "botao-tema-sol");
        sol.append(icone("sol"));
        const lua = criar("span", "botao-tema-lua");
        lua.append(icone("lua"));
        b.append(sol, lua, criar("span", "botao-tema-bolinha"));

        const atualizar = () => {
            const escuro = window.ItsJoiasTema.atual() === "escuro";
            b.setAttribute("aria-checked", String(escuro));
            const rotulo = escuro ? "Tema escuro ativo. Mudar para o tema claro" : "Tema claro ativo. Mudar para o tema escuro";
            b.setAttribute("aria-label", rotulo);
            b.title = escuro ? "Mudar para o tema claro" : "Mudar para o tema escuro";
        };
        b.addEventListener("click", () => window.ItsJoiasTema.alternar());
        document.addEventListener("itsjoias:tema", atualizar);
        atualizar();
        return b;
    }

    // telas sem cabeçalho de usuária (login) marcam o lugar do botão com data-botao-tema
    document.addEventListener("DOMContentLoaded", () => {
        document.querySelectorAll("[data-botao-tema]").forEach((lugar) => lugar.replaceChildren(botaoTema()));
    });

    Object.assign(window.ItsJoias, { perguntar, notificar, icone, botaoTema, modal: { abrir, fechar } });
})();
