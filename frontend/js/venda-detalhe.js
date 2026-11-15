// Cartão de uma venda, usado na ficha da cliente (clientes.html) e na aba Vendas (historico-vendas.html).
// Fechado mostra o número, a data, as peças, o total e quanto falta; aberto busca o detalhe
// (GET /api/vendas/{id}) e oferece Receber pagamento, Devolução e Cancelar venda.
//   ItsJoiasVendas.cartao(resumo, { mostrarCliente, aoMudar })
//   aoMudar(): chamado depois de um pagamento, devolução ou cancelamento (para recarregar a tela).
(function () {
    const { api, formato, criar, perguntar, notificar, modal } = window.ItsJoias;
    const mascaras = window.ItsJoiasMascaras;

    const SITUACAO = { FINALIZADA: "Finalizada", CANCELADA: "Cancelada" };
    const FORMAS = { PIX: "Pix", DINHEIRO: "Dinheiro", CARTAO: "Cartão", TRANSFERENCIA: "Transferência", OUTRO: "Outro" };

    /** O que falta pagar na venda (só para mostrar; quem confere o valor é o back-end). */
    function saldoDaVenda(d) {
        return d.parcelas.filter((p) => p.status === "ATIVA")
            .reduce((s, p) => s + Math.max(0, Number(p.valor) - Number(p.pago)), 0);
    }

    function recebidoDaVenda(d) {
        return d.pagamentos.filter((p) => !p.estornado).reduce((s, p) => s + Number(p.valor), 0);
    }

    function cartao(v, opcoes = {}) {
        const li = criar("li", "venda-cartao" + (v.status === "CANCELADA" ? " venda-cartao--cancelada" : ""));
        const topo = criar("button", "venda-cartao-topo");
        topo.type = "button";
        topo.setAttribute("aria-expanded", "false");
        const esquerda = criar("span", "venda-cartao-titulo");
        esquerda.append(criar("strong", null, opcoes.mostrarCliente ? `${v.clienteNome} · #${v.vendaId}` : `Venda #${v.vendaId}`),
            criar("span", "texto-suave", ` · ${formato.data(v.dataVenda)} · ` + formato.plural(v.quantidadePecas, "peça", "peças")),
            criar("span", "etiqueta-situacao etiqueta-" + v.status.toLowerCase(), SITUACAO[v.status] || v.status));
        if (v.atrasado !== undefined && Number(v.atrasado) > 0) {
            esquerda.append(criar("span", "chip chip--atraso", `${formato.reais(v.atrasado)} atrasado`));
        }
        if (opcoes.mostrarCliente && v.etiquetas) {
            esquerda.append(criar("span", "venda-cartao-etiquetas", v.etiquetas));
        }
        const direita = criar("span", "venda-cartao-valores");
        direita.append(criar("strong", "dinheiro", formato.reais(v.valorTotal)));
        if (v.status === "FINALIZADA") {
            direita.append(criar("span", "texto-suave", Number(v.saldo) > 0 ? `falta ${formato.reais(v.saldo)}` : "quitada"));
        }
        topo.append(esquerda, direita);
        const corpo = criar("div", "venda-cartao-corpo");
        corpo.hidden = true;
        topo.addEventListener("click", async () => {
            const abrir = corpo.hidden;
            corpo.hidden = !abrir;
            topo.setAttribute("aria-expanded", String(abrir));
            if (abrir && !corpo.dataset.carregado) await detalhar(v.vendaId, corpo, opcoes.aoMudar || (() => {}));
        });
        li.append(topo, corpo);
        return li;
    }

    async function detalhar(vendaId, corpo, aoMudar) {
        corpo.replaceChildren(criar("p", "carregando-texto", "Carregando detalhes…"));
        try {
            const d = await api("/api/vendas/" + vendaId);
            corpo.dataset.carregado = "1";
            const blocos = [];
            if (d.status === "CANCELADA") {
                const aviso = criar("div", "mensagem mensagem--alerta");
                aviso.append(criar("strong", null, "Venda cancelada em " + new Date(d.canceladaEm).toLocaleString("pt-BR",
                    { dateStyle: "short", timeStyle: "short" })), criar("p", null, "Motivo: " + d.motivoCancelamento));
                blocos.push(aviso);
            }
            const pecas = criar("ul", "venda-lista");
            d.itens.forEach((i) => {
                const l = criar("li");
                l.append(criar("span", null, `${i.etiqueta} · ${i.produto}`), criar("span", "texto-suave", i.maleta),
                    criar("span", "dinheiro", formato.reais(i.preco)));
                pecas.append(l);
            });
            blocos.push(criar("h5", null, "Peças"), pecas);
            if (Number(d.acrescimo) > 0) blocos.push(criar("p", "texto-suave", "Acréscimo de 10%: " + formato.reais(d.acrescimo)));
            const parcelas = criar("ul", "venda-lista");
            d.parcelas.forEach((p) => {
                const l = criar("li");
                const situacao = p.status === "CANCELADA" ? "cancelada" : Number(p.pago) >= Number(p.valor) ? "paga"
                    : Number(p.pago) > 0 ? `pago ${formato.reais(p.pago)}` : "em aberto";
                l.append(criar("span", null, `${p.numero}ª · vence ${formato.data(p.vencimento)}`),
                    criar("span", "texto-suave", situacao), criar("span", "dinheiro", formato.reais(p.valor)));
                parcelas.append(l);
            });
            blocos.push(criar("h5", null, "Parcelas"), parcelas);
            if (d.pagamentos.length) {
                const pagamentos = criar("ul", "venda-lista");
                d.pagamentos.forEach((p) => {
                    const l = criar("li", p.estornado ? "estornado" : null);
                    l.append(criar("span", null, `${formato.data(p.data)} · ${FORMAS[p.forma] || p.forma}`),
                        criar("span", "texto-suave", p.estornado ? "estornado" : "recebido"), criar("span", "dinheiro", formato.reais(p.valor)));
                    pagamentos.append(l);
                });
                blocos.push(criar("h5", null, "Pagamentos"), pagamentos);
            }
            blocos.push(criar("p", "texto-suave", "Registrada por " + (d.vendidaPor || "—") + "."));
            if (d.status === "FINALIZADA") {
                const acoesVenda = criar("div", "acoes");
                if (saldoDaVenda(d) > 0) {
                    const receber = criar("button", "botao-pequeno", "Receber pagamento");
                    receber.type = "button";
                    receber.addEventListener("click", () => receberPagamento(d, aoMudar));
                    acoesVenda.append(receber);
                }
                const devolucao = criar("button", "botao-secundario botao-pequeno", "Devolução");
                devolucao.type = "button";
                devolucao.dataset.somente = "PROPRIETARIA";
                devolucao.addEventListener("click", () => registrarDevolucao(d, aoMudar));
                const cancelar = criar("button", "botao-perigo botao-pequeno", "Cancelar venda");
                cancelar.type = "button";
                cancelar.dataset.somente = "PROPRIETARIA";
                cancelar.addEventListener("click", () => cancelarVenda(d, aoMudar));
                acoesVenda.append(devolucao, cancelar);
                blocos.push(acoesVenda);
            }
            corpo.replaceChildren(...blocos);
        } catch (erro) {
            corpo.replaceChildren(criar("p", "mensagem mensagem--erro", "Não foi possível carregar a venda. " + erro.message));
        }
    }

    // ---- cancelar e devolver --------------------------------------------------------------

    async function cancelarVenda(d, aoMudar) {
        const motivo = await perguntar({
            tipo: "perigo",
            titulo: `Cancelar a venda #${d.vendaId}?`,
            texto: ["As peças voltam a ficar disponíveis na maleta de onde saíram e as parcelas deixam de ser cobradas.",
                "A venda não é apagada: fica no histórico como cancelada, com o motivo."],
            detalhes: [["Cliente", d.clienteNome], ["Total", formato.reais(d.valorTotal)],
                ["Peças", String(d.itens.length)], ["Pagamentos estornados", formato.reais(recebidoDaVenda(d))]],
            campo: { rotulo: "Motivo do cancelamento", placeholder: "Ex.: lançada para a cliente errada", minimo: 3 },
            sim: "Cancelar venda", nao: "Manter venda",
        });
        if (!motivo) return;
        try {
            await api(`/api/vendas/${d.vendaId}/cancelar`, { metodo: "POST", corpo: { motivo } });
            notificar({ tipo: "sucesso", titulo: `Venda #${d.vendaId} cancelada.`, texto: "As peças voltaram para a maleta." });
            await aoMudar();
        } catch (erro) {
            notificar({ tipo: "erro", titulo: "Não foi possível cancelar.", texto: erro.message });
        }
    }

    /** A cliente devolve as peças (com a etiqueta): a venda inteira volta, pelo cancelamento. */
    async function registrarDevolucao(d, aoMudar) {
        const recebido = recebidoDaVenda(d);
        const motivo = await perguntar({
            tipo: "alerta",
            titulo: `Devolução da venda #${d.vendaId}?`,
            texto: ["Só aceite a devolução com a etiqueta na peça.",
                "Todas as peças desta venda voltam para a maleta de onde saíram e as parcelas deixam de ser cobradas."
                + (recebido > 0 ? ` A cliente recebe de volta ${formato.reais(recebido)}.` : "")],
            detalhes: [["Cliente", d.clienteNome], ["Peças", d.itens.map((i) => i.etiqueta).join(", ")],
                ["Devolver à cliente", formato.reais(recebido)]],
            campo: { rotulo: "Motivo da devolução", placeholder: "Ex.: não serviu, trocou de ideia…", minimo: 3 },
            sim: "Registrar devolução", nao: "Voltar",
        });
        if (!motivo) return;
        try {
            await api(`/api/vendas/${d.vendaId}/cancelar`, { metodo: "POST", corpo: { motivo: "Devolução: " + motivo } });
            notificar({
                tipo: "sucesso", titulo: `Devolução da venda #${d.vendaId} registrada.`,
                texto: "As peças voltaram para a maleta." + (recebido > 0 ? ` Devolva ${formato.reais(recebido)} à cliente.` : ""),
            });
            await aoMudar();
        } catch (erro) {
            notificar({ tipo: "erro", titulo: "Não foi possível registrar a devolução.", texto: erro.message });
        }
    }

    // ---- receber pagamento (uma parcela, parte ou tudo) ------------------------------------

    let janela = null;
    let vendaRecebendo = null;
    let depoisDeReceber = null;

    function montarJanela() {
        janela = criar("dialog", "modal");
        janela.setAttribute("aria-labelledby", "receber-titulo");
        janela.innerHTML = `
            <div class="modal-cabecalho">
                <div>
                    <h3 id="receber-titulo">Receber pagamento</h3>
                    <span class="texto-suave" id="receber-saldo"></span>
                </div>
                <button type="button" class="botao-fechar" data-fechar-modal aria-label="Fechar">✕</button>
            </div>
            <div class="modal-corpo">
                <div id="receber-mensagem" class="mensagem mensagem--erro" role="alert" hidden></div>
                <form id="form-receber" novalidate>
                    <div class="form-grade">
                        <div class="campo">
                            <label for="receber-valor">Valor recebido</label>
                            <input type="text" id="receber-valor" name="valor" inputmode="decimal" autocomplete="off" required>
                            <span class="dica">Abate da parcela que vence primeiro.</span>
                        </div>
                        <div class="campo">
                            <label for="receber-forma">Forma de pagamento</label>
                            <select id="receber-forma" name="forma">
                                ${Object.entries(FORMAS).map(([valor, nome]) => `<option value="${valor}">${nome}</option>`).join("")}
                            </select>
                        </div>
                        <div class="campo">
                            <label for="receber-data">Dia do pagamento</label>
                            <input type="date" id="receber-data" name="data">
                        </div>
                    </div>
                    <div class="acoes">
                        <button type="submit" id="receber-confirmar">Registrar pagamento</button>
                        <button type="button" class="botao-secundario" id="receber-quitar">Quitar tudo</button>
                    </div>
                </form>
            </div>`;
        document.body.append(janela);
        const q = (s) => janela.querySelector(s);
        q("#receber-quitar").addEventListener("click", () => {
            // quitar = receber exatamente o que falta, já registrando
            q("#receber-valor").value = valorEmTexto(saldoDaVenda(vendaRecebendo));
            q("#form-receber").requestSubmit();
        });
        q("#form-receber").addEventListener("submit", enviarPagamento);
    }

    function hojeISO() {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    }

    function valorEmTexto(n) {
        return n.toFixed(2).replace(".", ",");
    }

    function receberPagamento(d, aoMudar) {
        if (!janela) montarJanela();
        vendaRecebendo = d;
        depoisDeReceber = aoMudar;
        const q = (s) => janela.querySelector(s);
        q("#receber-titulo").textContent = `Receber da venda #${d.vendaId}`;
        q("#receber-saldo").textContent = `${d.clienteNome} · falta ${formato.reais(saldoDaVenda(d))}`;
        q("#receber-valor").value = valorEmTexto(saldoDaVenda(d));
        q("#receber-data").value = hojeISO();
        q("#receber-data").max = hojeISO();
        q("#receber-mensagem").hidden = true;
        modal.abrir(janela);
        q("#receber-valor").focus();
        q("#receber-valor").select();
    }

    async function enviarPagamento(evento) {
        evento.preventDefault();
        const q = (s) => janela.querySelector(s);
        const mensagem = q("#receber-mensagem");
        const valor = mascaras.reaisDigitados(q("#receber-valor").value);
        if (valor === null || Number.isNaN(valor) || Number(valor) <= 0) {
            mensagem.textContent = "Escreva o valor recebido, por exemplo 50,00.";
            mensagem.hidden = false;
            q("#receber-valor").focus();
            return;
        }
        const botao = q("#receber-confirmar");
        botao.disabled = true;
        botao.classList.add("processando");
        try {
            const d = await api(`/api/vendas/${vendaRecebendo.vendaId}/pagamentos`, {
                metodo: "POST", corpo: { valor, formaPagamento: q("#receber-forma").value, data: q("#receber-data").value || null },
            });
            const falta = saldoDaVenda(d);
            modal.fechar(janela);
            notificar({
                tipo: "sucesso", titulo: falta > 0 ? `Pagamento registrado na venda #${d.vendaId}.` : `Venda #${d.vendaId} quitada.`,
                texto: falta > 0 ? `Recebido ${formato.reais(valor)}. Ainda falta ${formato.reais(falta)}.` : `Recebido ${formato.reais(valor)}.`,
            });
            await depoisDeReceber();
        } catch (erro) {
            mensagem.textContent = erro.message;
            mensagem.hidden = false;
        } finally {
            botao.disabled = false;
            botao.classList.remove("processando");
        }
    }

    window.ItsJoiasVendas = { cartao };
})();
