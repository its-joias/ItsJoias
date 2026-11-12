// Endereço da API (back-end).
// Na máquina local, o back roda em localhost:8080. Publicado, ele fica no Render.
// Este arquivo não tem segredo nenhum: só o endereço público da API.
(function () {
    const local = ["localhost", "127.0.0.1", ""].includes(window.location.hostname);
    window.ITSJOIAS_CONFIG = {
        API_URL: local ? "http://localhost:8080" : "https://itsjoias-api.onrender.com",
    };
})();
