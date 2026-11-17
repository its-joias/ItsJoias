// Tema claro/escuro. Carregado no <head>, antes do CSS desenhar a página, para não "piscar" no tema errado.
// A escolha fica guardada neste navegador, separada por usuária (a Leide e a Teste podem ter temas diferentes
// no mesmo computador). Sem escolha guardada, segue o tema do sistema (celular/computador).
(function () {
    const CHAVE = "itsjoias.tema";

    function idUsuaria() {
        try {
            const sessao = JSON.parse(localStorage.getItem("itsjoias.sessao"));
            return sessao && sessao.usuario && sessao.usuario.email ? sessao.usuario.email.toLowerCase() : null;
        } catch (_) {
            return null;
        }
    }

    function ler(chave) {
        try {
            const v = localStorage.getItem(chave);
            return v === "claro" || v === "escuro" ? v : null;
        } catch (_) {
            return null;
        }
    }

    function salvar(tema) {
        try {
            const id = idUsuaria();
            if (id !== null) localStorage.setItem(`${CHAVE}.${id}`, tema);
            localStorage.setItem(CHAVE, tema); // último usado: vale na tela de login
        } catch (_) {
            // navegador sem armazenamento (aba anônima bloqueada): o tema vale só nesta página
        }
    }

    function inicial() {
        const id = idUsuaria();
        return (id !== null && ler(`${CHAVE}.${id}`)) || ler(CHAVE)
            || (window.matchMedia && matchMedia("(prefers-color-scheme: light)").matches ? "claro" : "escuro");
    }

    function aplicar(tema) {
        document.documentElement.dataset.tema = tema;
        const meta = document.querySelector('meta[name="theme-color"]');
        if (meta) meta.content = tema === "claro" ? "#F4F1DE" : "#3D405B";
        document.dispatchEvent(new CustomEvent("itsjoias:tema", { detail: tema }));
    }

    function atual() {
        return document.documentElement.dataset.tema || "escuro";
    }

    /**
     * Troca o tema com transição: o tema novo se abre num círculo a partir de "origem" (o botão),
     * com a View Transitions API; sem ela, as cores passam devagar (classe trocando-tema, css/transicoes.css).
     */
    function alternar(origem) {
        const novo = atual() === "escuro" ? "claro" : "escuro";
        salvar(novo);
        const raiz = document.documentElement;
        if (!document.startViewTransition) {
            raiz.classList.add("trocando-tema");
            aplicar(novo);
            setTimeout(() => raiz.classList.remove("trocando-tema"), 500);
            return novo;
        }
        const r = origem && origem.getBoundingClientRect ? origem.getBoundingClientRect() : null;
        const x = r ? r.left + r.width / 2 : innerWidth - 40;
        const y = r ? r.top + r.height / 2 : 40;
        const raio = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
        const transicao = document.startViewTransition(() => aplicar(novo));
        transicao.ready.then(() => {
            raiz.animate(
                { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${raio}px at ${x}px ${y}px)`] },
                { duration: 650, easing: "cubic-bezier(0.2, 0.8, 0.2, 1)", pseudoElement: "::view-transition-new(root)" });
        }).catch(() => {});
        return novo;
    }

    aplicar(inicial());
    window.ItsJoiasTema = { atual, alternar };
})();
