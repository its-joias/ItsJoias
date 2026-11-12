// Páginas internas: exige login e mostra quem está usando, com o botão Sair.
// Esconder botões aqui é só conforto visual: quem decide o que cada perfil pode fazer é o back-end.

(function () {
    const sessao = window.ItsJoias.lerSessao();
    if (!sessao) {
        window.ItsJoias.irParaLogin();
        return;
    }

    const NOMES_PERFIL = { PROPRIETARIA: "Proprietária", DESENVOLVIMENTO: "Desenvolvimento" };
    const perfil = sessao.usuario ? sessao.usuario.perfil : "";
    document.documentElement.dataset.perfil = perfil;

    document.addEventListener("DOMContentLoaded", () => {
        const topo = document.getElementById("usuario-topo");
        if (topo) {
            const { criar } = window.ItsJoias;
            const quem = criar("span", "usuario-topo-nome");
            quem.append(criar("strong", null, sessao.usuario.nome), criar("span", null, NOMES_PERFIL[perfil] || perfil));
            const sair = criar("button", "botao-secundario botao-pequeno", "Sair");
            sair.type = "button";
            sair.addEventListener("click", () => window.ItsJoias.sair());
            topo.replaceChildren(window.ItsJoias.botaoTema(), quem, sair);
        }
    });
})();
