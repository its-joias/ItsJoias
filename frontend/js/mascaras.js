// Máscaras de digitação (telefone e CPF) e leitura de valores em reais digitados.
(function () {
    function telefone(valor) {
        const n = valor.replace(/\D/g, "").replace(/^55(?=\d{10,11}$)/, "").slice(0, 11);
        if (n.length > 10) return n.replace(/^(\d{2})(\d{5})(\d{0,4}).*/, "($1) $2-$3");     // celular
        if (n.length > 6) return n.replace(/^(\d{2})(\d{4})(\d{0,4}).*/, "($1) $2-$3");      // fixo
        if (n.length > 2) return n.replace(/^(\d{2})(\d{0,5})/, "($1) $2");
        return n.length ? "(" + n : "";
    }

    function cpf(valor) {
        const n = valor.replace(/\D/g, "").slice(0, 11);
        if (n.length > 9) return n.replace(/^(\d{3})(\d{3})(\d{3})(\d{1,2}).*/, "$1.$2.$3-$4");
        if (n.length > 6) return n.replace(/^(\d{3})(\d{3})(\d{1,3}).*/, "$1.$2.$3");
        if (n.length > 3) return n.replace(/^(\d{3})(\d{1,3}).*/, "$1.$2");
        return n;
    }

    function aplicar(campo, formatar) {
        campo.addEventListener("input", () => { campo.value = formatar(campo.value); });
    }

    // "1.234,50" ou "1234.5" -> "1234.50" (texto enviado ao back; quem faz conta é o back)
    function reaisDigitados(texto) {
        const limpo = String(texto || "").trim().replace(/[R$\s]/g, "");
        if (!limpo) return null;
        const normal = limpo.includes(",") ? limpo.replace(/\./g, "").replace(",", ".") : limpo;
        return /^\d+(\.\d{1,2})?$/.test(normal) ? normal : NaN;
    }

    window.ItsJoiasMascaras = { telefone, cpf, aplicar, reaisDigitados };
})();
