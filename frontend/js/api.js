// Comunicação com o back-end e sessão de login.
//
// - Toda chamada passa por ItsJoias.api(): ela coloca o token de login no cabeçalho,
//   renova a sessão quando está para vencer e transforma erros em mensagens claras.
// - A sessão (tokens do Supabase Auth, devolvidos pelo back) fica no localStorage do
//   navegador, para continuar logada ao reabrir a página. Senha nunca é guardada.

(function () {
    const SESSAO = "itsjoias.sessao";
    const API_URL = window.ITSJOIAS_CONFIG.API_URL;

    class ErroApi extends Error {
        constructor(status, mensagem, campos) {
            super(mensagem);
            this.status = status;
            this.campos = campos || {};
        }
    }

    function lerSessao() {
        try {
            return JSON.parse(localStorage.getItem(SESSAO));
        } catch (_) {
            return null;
        }
    }

    function salvarSessao(sessao) {
        localStorage.setItem(SESSAO, JSON.stringify(sessao));
    }

    function limparSessao() {
        localStorage.removeItem(SESSAO);
    }

    function irParaLogin() {
        const voltar = encodeURIComponent(location.pathname.split("/").pop() + location.search);
        location.href = "login.html?voltar=" + voltar;
    }

    async function chamar(caminho, metodo, corpo, token) {
        const headers = { Accept: "application/json" };
        const arquivo = corpo instanceof FormData; // envio de arquivo: o navegador monta o cabeçalho sozinho
        if (corpo !== undefined && !arquivo) headers["Content-Type"] = "application/json";
        if (token) headers.Authorization = "Bearer " + token;
        try {
            return await fetch(API_URL + caminho, {
                method: metodo,
                headers,
                body: corpo === undefined ? undefined : arquivo ? corpo : JSON.stringify(corpo),
            });
        } catch (erro) {
            console.error(erro);
            throw new ErroApi(0, "Não foi possível falar com o servidor. Confira a internet e tente de novo. "
                + "(Se o sistema ficou um tempo sem uso, o servidor pode levar até 1 minuto para acordar.)");
        }
    }

    async function lerErro(resposta) {
        let corpo = null;
        try {
            corpo = await resposta.json();
        } catch (_) {
            // resposta sem JSON
        }
        const mensagem = (corpo && corpo.mensagem) || `O servidor respondeu com erro ${resposta.status}.`;
        return new ErroApi(resposta.status, mensagem, corpo && corpo.campos);
    }

    let renovando = null;

    async function renovar() {
        const sessao = lerSessao();
        if (!sessao || !sessao.refreshToken) return false;
        // várias chamadas ao mesmo tempo esperam a mesma renovação
        renovando = renovando || (async () => {
            const resposta = await chamar("/api/auth/renovar", "POST", { refreshToken: sessao.refreshToken });
            if (!resposta.ok) return false;
            salvarSessao(await resposta.json());
            return true;
        })().finally(() => { renovando = null; });
        return renovando;
    }

    /**
     * Chama a API. opcoes: { metodo: "GET", corpo: {...}, publico: false }
     * Devolve o JSON da resposta (ou null quando não há corpo). Lança ErroApi em caso de erro.
     */
    async function api(caminho, opcoes = {}) {
        const metodo = opcoes.metodo || "GET";
        if (opcoes.publico) {
            const resposta = await chamar(caminho, metodo, opcoes.corpo);
            if (!resposta.ok) throw await lerErro(resposta);
            return resposta.status === 204 ? null : resposta.json();
        }

        let sessao = lerSessao();
        if (!sessao) {
            irParaLogin();
            throw new ErroApi(401, "Entre no sistema para continuar.");
        }
        // renova um pouco antes de vencer
        if (sessao.expiraEm && sessao.expiraEm - 60 < Date.now() / 1000) {
            await renovar();
            sessao = lerSessao();
        }

        let resposta = await chamar(caminho, metodo, opcoes.corpo, sessao.accessToken);
        if (resposta.status === 401 && await renovar()) {
            resposta = await chamar(caminho, metodo, opcoes.corpo, lerSessao().accessToken);
        }
        if (resposta.status === 401) {
            limparSessao();
            irParaLogin();
            throw await lerErro(resposta);
        }
        if (!resposta.ok) throw await lerErro(resposta);
        return resposta.status === 204 ? null : resposta.json();
    }

    async function entrar(email, senha) {
        const sessao = await api("/api/auth/entrar", { metodo: "POST", corpo: { email, senha }, publico: true });
        salvarSessao(sessao);
        return sessao;
    }

    async function sair() {
        const sessao = lerSessao();
        if (sessao) {
            try {
                await chamar("/api/auth/sair", "POST", undefined, sessao.accessToken);
            } catch (_) {
                // sem rede: a sessão local é apagada mesmo assim
            }
        }
        limparSessao();
        location.href = "login.html";
    }

    // ---- formatação (só exibição; nenhuma conta de dinheiro é feita no front) -------------

    const formatoReais = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

    const formato = {
        reais: (valor) => formatoReais.format(valor),
        // "2026-12-05" -> "05/12/2026" (sem new Date(), que trocaria o dia por causa do fuso)
        data: (iso) => {
            const [ano, mes, dia] = iso.split("-");
            return `${dia}/${mes}/${ano}`;
        },
        plural: (n, um, varios) => `${n} ${n === 1 ? um : varios}`,
    };

    function criar(tag, classe, texto) {
        const el = document.createElement(tag);
        if (classe) el.className = classe;
        if (texto !== undefined && texto !== null) el.textContent = texto;
        return el;
    }

    window.ItsJoias = { api, ErroApi, entrar, sair, lerSessao, irParaLogin, formato, criar };
})();
