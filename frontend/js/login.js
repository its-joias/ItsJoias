// Tela de login.
(function () {
    const { entrar, lerSessao } = window.ItsJoias;

    // Volta para a página que pediu o login (só páginas do próprio sistema).
    function destino() {
        const voltar = new URLSearchParams(location.search).get("voltar") || "";
        return /^[a-z-]+\.html(\?[\w=&%-]*)?$/i.test(voltar) && !voltar.startsWith("login") ? voltar : "index.html";
    }

    if (lerSessao()) {
        location.replace(destino());
        return;
    }

    const form = document.getElementById("form-login");
    const email = document.getElementById("email");
    const senha = document.getElementById("senha");
    const botao = document.getElementById("botao-entrar");
    const mensagem = document.getElementById("login-mensagem");
    const mostrar = document.getElementById("mostrar-senha");

    mostrar.addEventListener("click", () => {
        const visivel = senha.type === "text";
        senha.type = visivel ? "password" : "text";
        mostrar.textContent = visivel ? "Mostrar" : "Ocultar";
        mostrar.setAttribute("aria-pressed", String(!visivel));
        senha.focus();
    });

    function mostrarErro(texto) {
        mensagem.textContent = texto;
        mensagem.hidden = false;
    }

    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        mensagem.hidden = true;
        if (!email.value.trim() || !senha.value) {
            mostrarErro("Preencha o e-mail e a senha.");
            return;
        }
        botao.disabled = true;
        botao.textContent = "Entrando…";
        try {
            await entrar(email.value.trim(), senha.value);
            location.replace(destino());
        } catch (erro) {
            mostrarErro(erro.message);
            senha.select();
        } finally {
            botao.disabled = false;
            botao.textContent = "Entrar";
        }
    });
})();
