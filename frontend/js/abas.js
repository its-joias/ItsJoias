// Abas das fichas (cliente, maleta) e de Relatórios: botões role="tab" dentro de .abas.
// Cada botão mostra o painel do seu aria-controls e esconde os outros. Setas esquerda/direita andam entre elas.
//   ItsJoiasAbas.ligar(lista, aoTrocar)   aoTrocar(idDoPainel): para carregar o conteúdo só quando a aba abre
(function () {
    function ligar(lista, aoTrocar) {
        const abas = [...lista.querySelectorAll('[role="tab"]')];

        function mostrar(aba, focar) {
            abas.forEach((a) => {
                const ativa = a === aba;
                a.setAttribute("aria-selected", String(ativa));
                a.tabIndex = ativa ? 0 : -1;
                document.getElementById(a.getAttribute("aria-controls")).hidden = !ativa;
            });
            if (focar) aba.focus();
            if (aoTrocar) aoTrocar(aba.getAttribute("aria-controls"));
        }

        abas.forEach((a, i) => {
            a.addEventListener("click", () => mostrar(a, false));
            a.addEventListener("keydown", (e) => {
                if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
                e.preventDefault();
                const passo = e.key === "ArrowRight" ? 1 : -1;
                mostrar(abas[(i + passo + abas.length) % abas.length], true);
            });
        });

        const inicial = abas.find((a) => a.getAttribute("aria-selected") === "true") || abas[0];
        mostrar(inicial, false);
        return { mostrar: (idPainel) => mostrar(abas.find((a) => a.getAttribute("aria-controls") === idPainel), false) };
    }

    window.ItsJoiasAbas = { ligar };
})();
