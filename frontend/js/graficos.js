// Gráficos do Its Joias em SVG puro (sem biblioteca). As cores vêm do CSS (variáveis --barra-*), então
// acompanham o tema claro/escuro sozinhas. As contas são feitas no back; aqui só se desenha.
//
//   ItsJoiasGraficos.colunas(caixa, { categorias, series, empilhar, formatar, destacar, titulo })
//   ItsJoiasGraficos.barras(caixa, { categorias, series, formatar, titulo })   (horizontais, empilhadas)
//   series: [{ nome, cor: "var(--barra-vendidas)", valores: [..] }]
(function () {
    const SVG = "http://www.w3.org/2000/svg";
    const { criar } = window.ItsJoias;
    const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

    function svg(tag, atributos) {
        const e = document.createElementNS(SVG, tag);
        Object.entries(atributos || {}).forEach(([k, v]) => e.setAttribute(k, v));
        return e;
    }

    /** "2026-11" -> "nov/26" */
    function mes(aaaaMm) {
        const [ano, m] = aaaaMm.split("-");
        return `${MESES[Number(m) - 1]}/${ano.slice(2)}`;
    }

    const reais = (v) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v);

    /** Eixo em reais: R$ 0, R$ 500, R$ 1.000 ... e R$ 10 mil a partir de 10.000. */
    function reaisEixo(v) {
        if (v >= 10000) return "R$ " + new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(v / 1000) + " mil";
        return "R$ " + new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 }).format(v);
    }

    /** Topo "redondo" e passos limpos (1, 2, 2,5, 5 × 10^n). */
    function escala(maximo, partes = 4) {
        if (!(maximo > 0)) return { topo: partes, passo: 1 };
        const bruto = maximo / partes;
        const potencia = 10 ** Math.floor(Math.log10(bruto));
        const passo = [1, 2, 2.5, 5, 10].map((f) => f * potencia).find((p) => p >= bruto);
        return { topo: Math.ceil(maximo / passo) * passo, passo };
    }

    /** Retângulo com só os cantos de cima (ou da direita) arredondados: a ponta do dado. */
    function pontaArredondada(x, y, largura, altura, raio, horizontal) {
        const r = Math.max(0, Math.min(raio, horizontal ? altura / 2 : largura / 2, horizontal ? largura : altura));
        if (horizontal) {
            return `M${x},${y} H${x + largura - r} Q${x + largura},${y} ${x + largura},${y + r} V${y + altura - r} `
                + `Q${x + largura},${y + altura} ${x + largura - r},${y + altura} H${x} Z`;
        }
        return `M${x},${y + altura} V${y + r} Q${x},${y} ${x + r},${y} H${x + largura - r} Q${x + largura},${y} `
            + `${x + largura},${y + r} V${y + altura} Z`;
    }

    // ---- moldura comum: legenda, balão, tabela ------------------------------------------------

    function montarMoldura(caixa, o) {
        caixa.replaceChildren();
        caixa.classList.add("grafico");
        if (o.series.length > 1) {
            const legenda = criar("ul", "grafico-legenda");
            o.series.forEach((s) => {
                const li = criar("li");
                const cor = criar("span", "grafico-amostra");
                cor.style.background = s.cor;
                li.append(cor, s.nome);
                legenda.append(li);
            });
            caixa.append(legenda);
        }
        const area = criar("div", "grafico-area");
        const balao = criar("div", "grafico-balao");
        balao.hidden = true;
        balao.setAttribute("aria-hidden", "true");
        area.append(balao);
        caixa.append(area);

        // tabela: os mesmos números, para quem não enxerga o gráfico ou quer copiar
        const botao = criar("button", "botao-secundario botao-pequeno grafico-botao-tabela", "Ver tabela");
        botao.type = "button";
        const tabela = criar("table", "grafico-tabela");
        tabela.hidden = true;
        const cab = criar("tr");
        cab.append(criar("th", null, o.rotuloCategoria || ""));
        o.series.forEach((s) => cab.append(criar("th", "numero", s.nome)));
        if (o.series.length > 1 && o.empilhar !== false) cab.append(criar("th", "numero", "Total"));
        const thead = criar("thead");
        thead.append(cab);
        const tbody = criar("tbody");
        o.categorias.forEach((c, i) => {
            const tr = criar("tr");
            tr.append(criar("td", null, c));
            let total = 0;
            o.series.forEach((s) => { total += s.valores[i]; tr.append(criar("td", "numero", o.formatar(s.valores[i]))); });
            if (o.series.length > 1 && o.empilhar !== false) tr.append(criar("td", "numero", o.formatar(total)));
            tbody.append(tr);
        });
        tabela.append(thead, tbody);
        botao.addEventListener("click", () => {
            tabela.hidden = !tabela.hidden;
            botao.textContent = tabela.hidden ? "Ver tabela" : "Ocultar tabela";
        });
        caixa.append(botao, tabela);
        return { area, balao };
    }

    function mostrarBalao(area, balao, o, i, x, y) {
        balao.replaceChildren(criar("strong", "grafico-balao-titulo", o.categorias[i]));
        let total = 0;
        o.series.forEach((s) => {
            total += s.valores[i];
            const linha = criar("div", "grafico-balao-linha");
            const cor = criar("span", "grafico-amostra");
            cor.style.background = s.cor;
            linha.append(cor, criar("span", null, s.nome), criar("strong", null, o.formatar(s.valores[i])));
            balao.append(linha);
        });
        if (o.series.length > 1 && o.empilhar !== false) {
            const linha = criar("div", "grafico-balao-linha grafico-balao-total");
            linha.append(criar("span"), criar("span", null, "Total"), criar("strong", null, o.formatar(total)));
            balao.append(linha);
        }
        (o.extraBalao ? o.extraBalao(i) : []).forEach((t) => balao.append(criar("div", "grafico-balao-extra", t)));
        balao.hidden = false;
        const larguraArea = area.clientWidth;
        const larguraBalao = balao.offsetWidth;
        balao.style.left = Math.max(0, Math.min(larguraArea - larguraBalao, x - larguraBalao / 2)) + "px";
        balao.style.top = Math.max(0, y - balao.offsetHeight - 10) + "px";
    }

    function redesenharAoMudarTamanho(caixa, desenhar) {
        let ultimaLargura = 0;
        const observador = new ResizeObserver(() => {
            const largura = Math.round(caixa.clientWidth);
            if (largura && largura !== ultimaLargura) {
                ultimaLargura = largura;
                desenhar(largura);
            }
        });
        observador.observe(caixa);
    }

    // ---- colunas (verticais): uma série ou empilhadas ---------------------------------------------

    function colunas(caixa, opcoes) {
        const o = { empilhar: true, formatar: reais, formatarEixo: reaisEixo, ...opcoes };
        const { area, balao } = montarMoldura(caixa, o);
        const desenhar = (largura) => {
            area.querySelectorAll("svg").forEach((e) => e.remove());
            const altura = 220;
            const margem = { topo: 18, direita: 4, baixo: 26, esquerda: 64 };
            const w = largura - margem.esquerda - margem.direita;
            const h = altura - margem.topo - margem.baixo;
            const totais = o.categorias.map((_, i) => o.series.reduce((s, serie) => s + serie.valores[i], 0));
            const { topo, passo } = escala(Math.max(...totais));
            const y = (v) => margem.topo + h - (v / topo) * h;
            const faixa = w / o.categorias.length;
            const larguraColuna = Math.min(24, Math.max(6, faixa * 0.6));

            const s = svg("svg", { width: largura, height: altura, role: "img", "aria-label": o.titulo });
            const alvos = svg("g", { class: "grafico-alvos" }); // atrás de tudo, na ordem dos meses (teclado)
            s.append(alvos);
            // grade e eixo (recessivos)
            for (let v = 0; v <= topo + 1e-9; v += passo) {
                const yy = Math.round(y(v)) + 0.5;
                s.append(svg("line", { x1: margem.esquerda, x2: largura - margem.direita, y1: yy, y2: yy, class: "grafico-grade" }));
                const t = svg("text", { x: margem.esquerda - 8, y: yy + 4, class: "grafico-eixo", "text-anchor": "end" });
                t.textContent = o.formatarEixo(v);
                s.append(t);
            }
            // rótulos de mês: pula alternados quando não cabem
            const pulo = faixa < 38 ? 2 : 1;
            o.categorias.forEach((c, i) => {
                if ((o.categorias.length - 1 - i) % pulo !== 0) return;
                const t = svg("text", { x: margem.esquerda + faixa * i + faixa / 2, y: altura - 8, class: "grafico-eixo", "text-anchor": "middle" });
                t.textContent = c;
                s.append(t);
            });
            // colunas
            o.categorias.forEach((c, i) => {
                const x = margem.esquerda + faixa * i + (faixa - larguraColuna) / 2;
                let base = 0;
                const visiveis = o.series.map((serie, k) => ({ serie, k })).filter(({ serie }) => serie.valores[i] > 0);
                visiveis.forEach(({ serie }, n) => {
                    const v = serie.valores[i];
                    const yTopo = y(base + v);
                    const yBase = y(base) - (n > 0 ? 2 : 0); // 2px de espaço entre partes empilhadas
                    const alturaParte = Math.max(1, yBase - yTopo);
                    const ultima = n === visiveis.length - 1;
                    const d = ultima ? pontaArredondada(x, yTopo, larguraColuna, alturaParte, 4, false)
                        : `M${x},${yTopo} H${x + larguraColuna} V${yTopo + alturaParte} H${x} Z`;
                    const parte = svg("path", { d, class: "grafico-marca" });
                    parte.style.fill = serie.cor;
                    s.append(parte);
                    base += v;
                });
                // valor só nos destaques (o maior e o último), nunca em todos
                if ((o.destacar || []).includes(i) && totais[i] > 0) {
                    const t = svg("text", { x: x + larguraColuna / 2, y: y(totais[i]) - 6, class: "grafico-valor", "text-anchor": "middle" });
                    t.textContent = o.formatarEixo(totais[i]);
                    s.append(t);
                }
                // alvo de toque/mouse/teclado: a faixa inteira (maior que a coluna)
                const alvo = svg("rect", { x: margem.esquerda + faixa * i, y: margem.topo, width: faixa, height: h,
                    class: "grafico-alvo", tabindex: "0",
                    "aria-label": `${c}: ${o.series.map((serie) => `${serie.nome} ${o.formatar(serie.valores[i])}`).join(", ")}` });
                const mostrar = () => {
                    s.querySelectorAll(".grafico-alvo").forEach((a) => a.classList.remove("ativo"));
                    alvo.classList.add("ativo");
                    mostrarBalao(area, balao, o, i, x + larguraColuna / 2, y(totais[i]));
                };
                alvo.addEventListener("pointerenter", mostrar);
                alvo.addEventListener("focus", mostrar);
                alvo.addEventListener("pointerleave", () => { alvo.classList.remove("ativo"); balao.hidden = true; });
                alvo.addEventListener("blur", () => { alvo.classList.remove("ativo"); balao.hidden = true; });
                alvos.append(alvo);
            });
            area.append(s);
        };
        redesenharAoMudarTamanho(caixa, desenhar);
    }

    // ---- barras horizontais empilhadas (uma linha por categoria) --------------------------------

    function barras(caixa, opcoes) {
        const o = { formatar: (v) => String(v), ...opcoes, empilhar: true };
        const { area, balao } = montarMoldura(caixa, o);
        const desenhar = (largura) => {
            area.querySelectorAll("svg").forEach((e) => e.remove());
            const espessura = 18;
            const linha = 40;
            const margem = { topo: 4, esquerda: Math.min(130, Math.max(80, largura * 0.28)), direita: 44 };
            const altura = margem.topo + linha * o.categorias.length;
            const w = largura - margem.esquerda - margem.direita;
            const totais = o.categorias.map((_, i) => o.series.reduce((s, serie) => s + serie.valores[i], 0));
            const maximo = Math.max(1, ...totais);
            const s = svg("svg", { width: largura, height: altura, role: "img", "aria-label": o.titulo });
            const alvos = svg("g", { class: "grafico-alvos" });
            s.append(alvos);
            o.categorias.forEach((c, i) => {
                const yy = margem.topo + linha * i + (linha - espessura) / 2;
                const rotulo = svg("text", { x: margem.esquerda - 10, y: yy + espessura / 2 + 4, class: "grafico-categoria", "text-anchor": "end" });
                rotulo.textContent = c;
                s.append(rotulo);
                s.append(svg("line", { x1: margem.esquerda, x2: margem.esquerda, y1: yy - 6, y2: yy + espessura + 6, class: "grafico-grade" }));
                let x = margem.esquerda;
                const visiveis = o.series.filter((serie) => serie.valores[i] > 0);
                visiveis.forEach((serie, n) => {
                    const comprimento = (serie.valores[i] / maximo) * w - (n > 0 ? 2 : 0);
                    if (n > 0) x += 2; // 2px de espaço entre partes
                    const ultima = n === visiveis.length - 1;
                    const d = ultima ? pontaArredondada(x, yy, Math.max(1, comprimento), espessura, 4, true)
                        : `M${x},${yy} H${x + comprimento} V${yy + espessura} H${x} Z`;
                    const parte = svg("path", { d, class: "grafico-marca" });
                    parte.style.fill = serie.cor;
                    s.append(parte);
                    x += comprimento;
                });
                const total = svg("text", { x: x + 8, y: yy + espessura / 2 + 4, class: "grafico-valor" });
                total.textContent = o.formatar(totais[i]);
                s.append(total);
                const alvo = svg("rect", { x: 0, y: margem.topo + linha * i, width: largura, height: linha, class: "grafico-alvo",
                    tabindex: "0", "aria-label": `${c}: ${o.series.map((serie) => `${serie.nome} ${o.formatar(serie.valores[i])}`).join(", ")}` });
                const mostrar = () => {
                    s.querySelectorAll(".grafico-alvo").forEach((a) => a.classList.remove("ativo"));
                    alvo.classList.add("ativo");
                    mostrarBalao(area, balao, o, i, margem.esquerda + Math.min(w, (totais[i] / maximo) * w) / 2, yy);
                };
                alvo.addEventListener("pointerenter", mostrar);
                alvo.addEventListener("focus", mostrar);
                alvo.addEventListener("pointerleave", () => { alvo.classList.remove("ativo"); balao.hidden = true; });
                alvo.addEventListener("blur", () => { alvo.classList.remove("ativo"); balao.hidden = true; });
                alvos.append(alvo);
            });
            area.append(s);
        };
        redesenharAoMudarTamanho(caixa, desenhar);
    }

    // ---- pizza (parte de um todo) -----------------------------------------------------------
    // fatias: [{ nome, valor, cor, detalhe }]. Até 4 fatias; a última costuma ser "Outras".

    /** Texto dentro de uma fatia: branco ou quase-preto, conforme a cor da fatia (calculado na tela). */
    function corDoTextoSobre(elemento) {
        const m = getComputedStyle(elemento).fill.match(/\d+(\.\d+)?/g);
        if (!m) return "#172A33";
        const [r, g, b] = m.slice(0, 3).map(Number).map((c) => {
            const v = c / 255;
            return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
        });
        const luz = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return (1.05) / (luz + 0.05) >= (luz + 0.05) / 0.05 ? "#FFFFFF" : "#172A33";
    }

    function pizza(caixa, opcoes) {
        const o = { formatar: reais, ...opcoes };
        caixa.replaceChildren();
        caixa.classList.add("grafico", "grafico-pizza");
        const total = o.fatias.reduce((s, f) => s + f.valor, 0);
        const pct = (v) => new Intl.NumberFormat("pt-BR", { maximumFractionDigits: v / total < 0.1 ? 1 : 0 }).format((v / total) * 100) + "%";

        const corpo = criar("div", "grafico-pizza-corpo");
        const area = criar("div", "grafico-area grafico-pizza-area");
        const balao = criar("div", "grafico-balao");
        balao.hidden = true;
        balao.setAttribute("aria-hidden", "true");
        area.append(balao);

        // legenda com os números (a pizza sozinha não deixa comparar valores parecidos)
        const legenda = criar("ul", "grafico-pizza-legenda");
        o.fatias.forEach((f) => {
            const li = criar("li");
            const cor = criar("span", "grafico-amostra");
            cor.style.background = f.cor;
            const nome = criar("span", "grafico-pizza-nome", f.nome);
            li.append(cor, nome, criar("strong", null, o.formatar(f.valor)), criar("span", "texto-suave", pct(f.valor)));
            legenda.append(li);
        });
        const totalLi = criar("li", "grafico-pizza-total");
        totalLi.append(criar("span"), criar("span", null, "Total"), criar("strong", null, o.formatar(total)), criar("span"));
        legenda.append(totalLi);
        corpo.append(area, legenda);

        // tabela
        const botao = criar("button", "botao-secundario botao-pequeno grafico-botao-tabela", "Ver tabela");
        botao.type = "button";
        const tabela = criar("table", "grafico-tabela");
        tabela.hidden = true;
        const cab = criar("tr");
        [o.rotuloCategoria || "", "Valor", "Parte"].forEach((t, i) => cab.append(criar("th", i ? "numero" : null, t)));
        const thead = criar("thead");
        thead.append(cab);
        const tbody = criar("tbody");
        o.fatias.forEach((f) => {
            const tr = criar("tr");
            tr.append(criar("td", null, f.nome), criar("td", "numero", o.formatar(f.valor)), criar("td", "numero", pct(f.valor)));
            tbody.append(tr);
        });
        tabela.append(thead, tbody);
        botao.addEventListener("click", () => {
            tabela.hidden = !tabela.hidden;
            botao.textContent = tabela.hidden ? "Ver tabela" : "Ocultar tabela";
        });
        caixa.append(corpo, botao, tabela);

        const raio = 96;
        const tamanho = raio * 2 + 8;
        const centro = tamanho / 2;
        const s = svg("svg", { width: tamanho, height: tamanho, viewBox: `0 0 ${tamanho} ${tamanho}`, role: "img", "aria-label": o.titulo });
        let angulo = -Math.PI / 2; // começa no meio-dia, sentido horário
        const ponto = (a, r) => [centro + r * Math.cos(a), centro + r * Math.sin(a)];
        o.fatias.forEach((f, i) => {
            const fatiaAngulo = (f.valor / total) * Math.PI * 2;
            let forma;
            if (o.fatias.length === 1 || fatiaAngulo >= Math.PI * 2 - 1e-6) {
                forma = svg("circle", { cx: centro, cy: centro, r: raio });
            } else {
                const [x1, y1] = ponto(angulo, raio);
                const [x2, y2] = ponto(angulo + fatiaAngulo, raio);
                const grande = fatiaAngulo > Math.PI ? 1 : 0;
                forma = svg("path", { d: `M${centro},${centro} L${x1},${y1} A${raio},${raio} 0 ${grande} 1 ${x2},${y2} Z` });
            }
            forma.setAttribute("class", "grafico-fatia");
            forma.setAttribute("tabindex", "0");
            forma.setAttribute("aria-label", `${f.nome}: ${o.formatar(f.valor)}, ${pct(f.valor)}`);
            forma.style.fill = f.cor;
            s.append(forma);
            const meio = angulo + fatiaAngulo / 2;
            const mostrar = () => {
                s.classList.add("com-destaque");
                s.querySelectorAll(".grafico-fatia").forEach((e) => e.classList.toggle("ativa", e === forma));
                balao.replaceChildren(criar("strong", "grafico-balao-titulo", f.nome));
                const linha = criar("div", "grafico-balao-linha");
                const cor = criar("span", "grafico-amostra");
                cor.style.background = f.cor;
                linha.append(cor, criar("span", null, pct(f.valor)), criar("strong", null, o.formatar(f.valor)));
                balao.append(linha);
                if (f.detalhe) balao.append(criar("div", "grafico-balao-extra", f.detalhe));
                balao.hidden = false;
                const [bx, by] = ponto(meio, raio * 0.6);
                balao.style.left = Math.max(0, bx - balao.offsetWidth / 2) + "px";
                balao.style.top = Math.max(0, by - balao.offsetHeight - 12) + "px";
            };
            const esconder = () => {
                s.classList.remove("com-destaque");
                forma.classList.remove("ativa");
                balao.hidden = true;
            };
            forma.addEventListener("pointerenter", mostrar);
            forma.addEventListener("focus", mostrar);
            forma.addEventListener("pointerleave", esconder);
            forma.addEventListener("blur", esconder);
            f.forma = forma;
            f.meio = meio;
            f.fatiaAngulo = fatiaAngulo;
            angulo += fatiaAngulo;
        });
        area.append(s);
        // percentual só nas fatias grandes (>= 12%), com a cor de texto que contrasta com a fatia
        const rotulos = [];
        o.fatias.forEach((f) => {
            if (f.valor / total < 0.12) return;
            const [tx, ty] = o.fatias.length === 1 ? [centro, centro] : ponto(f.meio, raio * 0.62);
            const t = svg("text", { x: tx, y: ty + 4, class: "grafico-pizza-rotulo", "text-anchor": "middle" });
            t.textContent = pct(f.valor);
            t.style.fill = corDoTextoSobre(f.forma);
            s.append(t);
            rotulos.push([t, f.forma]);
        });
        // no outro tema as fatias mudam de cor: recalcula a cor do texto
        document.addEventListener("itsjoias:tema", () => {
            requestAnimationFrame(() => rotulos.forEach(([t, forma]) => { t.style.fill = corDoTextoSobre(forma); }));
        });
    }

    window.ItsJoiasGraficos = { colunas, barras, pizza, mes, reais };
})();
