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
        if (meta) meta.content = tema === "claro" ? "#FBF7EE" : "#0E1728";
        document.dispatchEvent(new CustomEvent("itsjoias:tema", { detail: tema }));
    }

    function atual() {
        return document.documentElement.dataset.tema || "escuro";
    }

    function alternar() {
        const novo = atual() === "escuro" ? "claro" : "escuro";
        salvar(novo);
        aplicar(novo);
        return novo;
    }

    aplicar(inicial());
    window.ItsJoiasTema = { atual, alternar };
})();
