// Painel da vendedora.
// Todos os números vêm prontos de GET /api/painel (via ItsJoias.api, com o login): aqui só se FORMATA e EXIBE.
// Nenhuma conta de dinheiro é feita no front.

const formatoReais = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const formatoPercentual = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

function reais(valor) {
    return formatoReais.format(valor);
}

// "2026-12-05" -> "05/12/2026" (sem new Date(), que trocaria o dia por causa do fuso)
function data(iso) {
    const [ano, mes, dia] = iso.split("-");
    return `${dia}/${mes}/${ano}`;
}

function plural(quantidade, singular, pluralTexto) {
    return `${quantidade} ${quantidade === 1 ? singular : pluralTexto}`;
}

function preencher(campo, texto) {
    document.querySelectorAll(`[data-campo="${campo}"]`).forEach((el) => {
        el.textContent = texto;
    });
}

function criar(tag, classe, texto) {
    const el = document.createElement(tag);
    if (classe) el.className = classe;
    if (texto !== undefined) el.textContent = texto;
    return el;
}

// ---------------------------------------------------------------------------
// Estados da tela
// ---------------------------------------------------------------------------

const elPainel = document.getElementById("painel");
const elAviso = document.getElementById("painel-aviso");
const elConteudo = document.getElementById("painel-conteudo");
const elData = document.getElementById("painel-data");

function mostrarCarregando() {
    elPainel.setAttribute("aria-busy", "true");
    elData.textContent = "Carregando o painel…";
    elAviso.className = "painel-aviso";
    elAviso.replaceChildren(criar("p", null, "Buscando os números atualizados…"));
    elAviso.hidden = false;
    elConteudo.hidden = true;
}

function mostrarErro(mensagem) {
    elPainel.setAttribute("aria-busy", "false");
    elData.textContent = "";
    elAviso.className = "painel-aviso painel-aviso--erro";
    const botao = criar("button", null, "Tentar de novo");
    botao.type = "button";
    botao.addEventListener("click", carregarPainel);
    elAviso.replaceChildren(
        criar("strong", null, "Não foi possível carregar o painel."),
        criar("p", null, mensagem),
        botao
    );
    elAviso.hidden = false;
    elConteudo.hidden = true;
}

function mostrarPainel(painel) {
    elPainel.setAttribute("aria-busy", "false");
    elData.textContent = `Números de hoje, ${data(painel.hoje)}.`;

    const bancoNovo = painel.totais.totalVendido.quantidadeVendas === 0
        && painel.maletasAtivas.length === 0
        && painel.parcelasAtrasadas.length === 0;
    if (bancoNovo) {
        elAviso.className = "painel-aviso";
        elAviso.replaceChildren(
            criar("strong", null, "Ainda não há vendas nem maletas cadastradas."),
            criar("p", null, "Assim que a primeira maleta ou venda for registrada, os números aparecem aqui.")
        );
        elAviso.hidden = false;
    } else {
        elAviso.hidden = true;
    }

    preencherCartoes(painel.totais);
    preencherAtrasadas(painel.parcelasAtrasadas, painel.totais.atrasado);
    preencherDia5(painel.vencemNoProximoDia5, painel.totais.proximoDia5);
    preencherMaletas(painel.maletasAtivas);
    elConteudo.hidden = false;
}

// ---------------------------------------------------------------------------
// Blocos
// ---------------------------------------------------------------------------

function preencherCartoes(t) {
    preencher("totalVendido", reais(t.totalVendido.valor));
    preencher("totalVendidoNota", plural(t.totalVendido.quantidadeVendas, "venda finalizada", "vendas finalizadas"));

    preencher("recebido", reais(t.recebido.valor));
    preencher("recebidoNota", t.recebido.percentualDoVendido === null
        ? "Nada vendido ainda"
        : `${formatoPercentual.format(t.recebido.percentualDoVendido)}% do vendido`);

    preencher("aReceber", reais(t.aReceber.valor));
    preencher("aReceberNota", plural(t.aReceber.quantidadeParcelas, "parcela em aberto", "parcelas em aberto"));

    preencher("atrasado", reais(t.atrasado.valor));
    preencher("atrasadoNota", t.atrasado.quantidadeParcelas === 0
        ? "Nenhuma parcela vencida"
        : plural(t.atrasado.quantidadeParcelas, "parcela vencida", "parcelas vencidas"));
    document.querySelector('[data-cartao="atrasado"]')
        .classList.toggle("cartao--alerta", t.atrasado.quantidadeParcelas > 0);

    preencher("clientesDevendo", String(t.clientesDevendo.quantidade));
    preencher("clientesDevendoNota", t.clientesDevendo.comParcelaAtrasada === 0
        ? "Nenhuma com atraso"
        : `${t.clientesDevendo.comParcelaAtrasada} com parcela atrasada`);

    preencher("proximoDia5", reais(t.proximoDia5.valor));
    preencher("proximoDia5Nota",
        `${data(t.proximoDia5.data)} · ${plural(t.proximoDia5.quantidadeParcelas, "parcela", "parcelas")}`);
}

function linhaParcela(nome, detalhe, valor) {
    const li = criar("li", "parcela");
    const info = criar("div", "parcela-info");
    info.append(criar("span", "parcela-cliente", nome), criar("span", "parcela-detalhe", detalhe));
    li.append(info, criar("span", "parcela-valor dinheiro", valor));
    return li;
}

function preencherAtrasadas(parcelas, totalAtrasado) {
    const lista = document.getElementById("lista-atrasadas");
    lista.replaceChildren(...parcelas.map((p) => {
        const li = linhaParcela(
            p.clienteNome,
            `Venda #${p.vendaId} · parcela ${p.numeroParcela}/${p.totalParcelas} · venceu em ${data(p.dataVencimento)}`,
            reais(p.saldo)
        );
        const dias = plural(p.diasAtraso, "dia", "dias");
        const chip = criar("span", "chip chip--atraso",
            p.situacao === "PARCIAL" ? `Parcial, ${dias}` : `${dias} de atraso`);
        const info = li.querySelector(".parcela-info");
        info.append(chip);
        if (p.situacao === "PARCIAL") {
            info.append(criar("span", "parcela-detalhe", `Já pagou ${reais(p.valorPago)} de ${reais(p.valorParcela)}`));
        }
        return li;
    }));
    document.getElementById("vazio-atrasadas").hidden = parcelas.length > 0;
    preencher("rodapeAtrasado", reais(totalAtrasado.valor));
}

function preencherDia5(parcelas, proximoDia5) {
    preencher("dataDia5", data(proximoDia5.data));
    const lista = document.getElementById("lista-dia5");
    lista.replaceChildren(...parcelas.map((p) => linhaParcela(
        p.clienteNome,
        `Venda #${p.vendaId} · parcela ${p.numeroParcela}/${p.totalParcelas}`,
        reais(p.saldo)
    )));
    document.getElementById("vazio-dia5").hidden = parcelas.length > 0;
    preencher("rodapeDia5", reais(proximoDia5.valor));
}

// Barra empilhada: a largura de cada parte é a contagem de peças (não é dinheiro).
function barraPecas(m) {
    const partes = [
        { classe: "cor-vendidas", nome: "vendidas", qtd: m.pecasVendidas },
        { classe: "cor-disponiveis", nome: "disponíveis", qtd: m.pecasDisponiveis },
        { classe: "cor-perdidas", nome: "perdidas ou devolvidas", qtd: m.pecasPerdidasOuDevolvidas },
    ];
    const resumo = partes.map((p) => `${p.qtd} ${p.nome}`).join(", ");

    const barra = criar("div", "barra");
    barra.setAttribute("role", "img");
    barra.setAttribute("aria-label", `${m.pecasRecebidas} peças recebidas: ${resumo}`);
    partes.filter((p) => p.qtd > 0).forEach((p) => {
        const parte = criar("span", `barra-parte ${p.classe}`);
        parte.style.flexGrow = p.qtd;
        parte.title = `${p.qtd} ${p.nome}`;
        barra.append(parte);
    });
    return barra;
}

function preencherMaletas(maletas) {
    const lista = document.getElementById("lista-maletas");
    lista.replaceChildren(...maletas.map((m) => {
        const li = criar("li", "maleta");

        const topo = criar("div", "maleta-topo");
        const titulo = criar("div");
        titulo.append(
            criar("span", "maleta-nome", m.nome),
            criar("span", "maleta-detalhe", `${m.fornecedor} · entrada em ${data(m.dataEntrada)}`)
        );
        topo.append(titulo, criar("span", "maleta-detalhe", plural(m.pecasRecebidas, "peça recebida", "peças recebidas")));

        const contagem = criar("p", "maleta-contagem");
        contagem.append(
            criar("span", null, plural(m.pecasVendidas, "vendida", "vendidas")),
            criar("span", null, plural(m.pecasDisponiveis, "disponível", "disponíveis")),
            criar("span", null, `${plural(m.pecasPerdidas, "perdida", "perdidas")} e ${plural(m.pecasDevolvidas, "devolvida", "devolvidas")} ao fornecedor`)
        );

        const valores = criar("dl", "maleta-valores");
        valores.append(
            criar("dt", null, "Custo"), criar("dd", "dinheiro", reais(m.custoTotal)),
            criar("dt", null, "Vendido"), criar("dd", "dinheiro", reais(m.totalVendido))
        );

        li.append(topo, barraPecas(m), contagem, valores);
        return li;
    }));
    document.getElementById("vazio-maletas").hidden = maletas.length > 0;
    document.querySelector(".legenda").hidden = maletas.length === 0;
}

// ---------------------------------------------------------------------------
// Carga
// ---------------------------------------------------------------------------

async function carregarPainel() {
    mostrarCarregando();
    try {
        mostrarPainel(await window.ItsJoias.api("/api/painel"));
    } catch (erro) {
        console.error(erro);
        mostrarErro(erro.message);
    }
}

document.addEventListener("DOMContentLoaded", carregarPainel);
