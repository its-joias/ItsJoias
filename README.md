<div align="center">

# 💍 ITS Joias - Sistema de Gestão de Vendas de Joias

![Versão](https://img.shields.io/badge/version-0.1.0-blue)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)
![Projeto](https://img.shields.io/badge/projeto-de%20estudo-purple)
![Frontend](https://img.shields.io/badge/frontend-HTML%20%2F%20CSS%20%2F%20JS-E34F26)
![Backend](https://img.shields.io/badge/backend-Java%20%2F%20Spring%20Boot%204-6DB33F)
![Banco](https://img.shields.io/badge/banco-PostgreSQL%2018-336791)

**Sistema de vendas da marca Leide Farias, "joias com alma baiana"**

[Sobre](#-sobre-o-projeto) •
[Funcionalidades](#-funcionalidades) •
[Tecnologias](#️-tecnologias) •
[Banco de Dados](#️-banco-de-dados) •
[Como Executar](#-como-executar) •
[Roadmap](#️-roadmap) •
[Equipe](#-equipe)

</div>

---

## 📖 Sobre o Projeto

O **ITS Joias** é um sistema web de organização de vendas desenvolvido para a marca **Leide Farias**. A vendedora recebe **maletas** de joias de fornecedores e vende para suas **clientes** à vista ou parcelado. O sistema controla o estoque de cada maleta, as parcelas, os pagamentos e quem está devendo.

> 🎓 **Projeto de estudo.** Este é um projeto pessoal, feito por duas amigas para aprender desenvolvimento full stack na prática. Ele resolve um problema real, mas o objetivo principal é aprender: ... modelagem de banco de dados, API REST com Spring Boot e interface web, hoje em HTML, CSS e JavaScript, com migração para React planejada para uma próxima fase.

### 🎯 Problema que resolvemos

- **Controle no caderno ou na planilha**, sujeito a erro e difícil de consultar
- **Não saber de qual maleta saiu cada peça**, o que atrapalha o acerto com o fornecedor
- **Parcelas e pagamentos espalhados**, sem visão clara de quem está devendo e há quanto tempo
- **Estoque que não bate** com o que foi vendido de verdade

### ✨ Nossa Solução

- ✅ Entrada de maleta com **cadastro automático das peças** e **etiqueta única para cada peça** (`C0001-01`)
- ✅ Venda rápida pela **etiqueta da peça**, ou só pelo modelo, com escolha automática da peça da maleta mais antiga
- ✅ **Rastreio peça por peça**: de qual maleta veio, para quem foi vendida, se foi perdida ou devolvida
- ✅ **Parcelamento** com cálculo automático e controle de cada pagamento
- ✅ Visão de **quem deve, quanto e há quantos dias**
- ✅ **Painel por maleta**: o que chegou, o que foi vendido, o que foi pago e o que está em aberto
- ✅ Banco de dados que **recusa sozinho** o que nunca pode acontecer (pagar a mais, estoque negativo, venda que não fecha a conta)

---

## 🚀 Funcionalidades

> As funcionalidades abaixo estão **planejadas**. Veja o andamento no [Roadmap](#️-roadmap).

### 🧳 Maletas e Estoque

| Funcionalidade | Descrição |
|----------------|-----------|
| **Fornecedores** | Cadastro das lojas e fábricas de onde vêm as maletas |
| **Entrada de Maleta** | Colar a lista da loja e conferir peça por peça antes de gravar |
| **Classificação Automática** | O sistema lê a descrição ("GARGANTILHA VENEZIANA...") e sugere a categoria |
| **Código Próprio** | Cada modelo ganha um código da casa (`C0001`, `A0042`) |
| **Etiqueta por Peça** | Cada unidade física ganha sua etiqueta (`C0001-01`, `C0001-02`...), gerada pelo banco |
| **Modelo Repetido** | Um modelo que volta numa maleta nova é reconhecido pelo código da loja e continua a numeração das etiquetas |
| **Situação da Peça** | Disponível, vendida, perdida ou devolvida ao fornecedor, peça por peça |
| **Histórico de Estoque** | Toda entrada, venda, perda ou devolução de cada peça fica registrada e não pode ser apagada |

### 🛍️ Vendas e Clientes

| Funcionalidade | Descrição |
|----------------|-----------|
| **Clientes** | Busca por nome e cadastro rápido com nome e telefone |
| **Carrinho** | Peças adicionadas pela etiqueta da peça ou pelo código do modelo, com aviso se não houver peça disponível |
| **Prévia da Venda** | Total, parcelas e de qual maleta sai cada peça, antes de confirmar |
| **Acréscimo** | Opção de valor original ou +10% |
| **Parcelamento** | Divisão automática em parcelas, com os centavos que sobram na última |
| **Cancelamento** | Estorno dos pagamentos e devolução das peças para a maleta de origem |

### 💰 Financeiro e Painéis

| Funcionalidade | Descrição |
|----------------|-----------|
| **Recebimentos** | Registro de pagamento por parcela (PIX, dinheiro, cartão, transferência) |
| **Estorno** | Pagamento lançado errado é estornado, nunca apagado |
| **Situação das Parcelas** | Pendente, parcial, paga ou atrasada, com dias de atraso |
| **Saldo por Cliente** | Total comprado, pago, em aberto e atrasado |
| **Resumo Geral** | Total vendido, recebido, a receber e clientes devendo |
| **Painel por Maleta** | Custo, valor vendido, pago e em aberto de cada remessa |
| **Cobranças** *(fase 1.5)* | Histórico de contatos e promessas de pagamento |

---

## 🛠️ Tecnologias

### Frontend

| Tecnologia | Finalidade |
|------------|------------|
| **HTML / CSS / JavaScript** | Interface atual (protótipo) |
| **React** *(planejado)* | Interface definitiva |

### Backend

| Tecnologia | Finalidade |
|------------|------------|
| **Java 21** | Linguagem principal |
| **Spring Boot 4** | Framework para a API REST |
| **Spring Data JPA / Hibernate 7** | Acesso ao banco de dados |
| **Bean Validation** | Validação dos dados recebidos |

### Banco de Dados

| Tecnologia | Finalidade |
|------------|------------|
| **PostgreSQL 18** | Banco de dados relacional |
| **pgAdmin 4** | Administração do banco |

---

## 🗄️ Banco de Dados

O banco é criado por um **script único**, [`database/its_joias_banco.sql`](database/its_joias_banco.sql), rodado no pgAdmin. O Spring não cria nem altera tabelas: ele só confere se o código bate com o banco (`ddl-auto=validate`).

### Princípio do desenho

> O banco guarda **fatos**: o que entrou, o que foi vendido, o que foi pago.
> Tudo que é **calculado** (saldo, atraso, estoque por maleta, totais) vem das **views** e nunca vira coluna.

### Visão geral

| Área | Tabelas |
|------|---------|
| **Acesso** | `usuarios` |
| **Clientes** | `clientes`, `cobrancas` |
| **Catálogo** | `categorias`, `categoria_palavras_chave`, `produtos`, `produto_codigos_fornecedor`, `produto_codigos_historico` |
| **Maletas** | `fornecedores`, `maletas`, `maleta_itens`, `pecas` |
| **Vendas** | `vendas`, `itens_venda`, `parcelas`, `pagamentos` |
| **Estoque** | `movimentacoes_estoque` |

**17 tabelas**, **10 views** e **21 triggers**.

### Produto, peça e maleta

| Conceito | Exemplo | O que é |
|----------|---------|---------|
| **Produto** | `C0001` | O modelo: gargantilha veneziana, com nome, categoria e preço |
| **Peça** | `C0001-01` | Cada unidade física desse modelo, com etiqueta própria |
| **Maleta** | Maleta Janeiro | A remessa de onde a peça veio |

A situação de cada peça (disponível, vendida, perdida, devolvida) vem do histórico de estoque dela.

### O que o banco recusa sozinho

As regras de negócio ficam no back-end. O banco é uma **rede de segurança** e recusa o que nunca pode acontecer:

- 🚫 Venda finalizada cuja soma dos itens ou das parcelas não fecha com o total
- 🚫 Pagamento acima do saldo da parcela, ou pagamento apagado ou editado
- 🚫 Cancelar uma venda sem antes estornar os pagamentos e devolver as peças
- 🚫 Vender a mesma peça duas vezes, mesmo com duas pessoas vendendo ao mesmo tempo
- 🚫 Devolver a mesma peça duas vezes, ou na venda errada
- 🚫 Fechar a entrada de uma maleta com etiqueta faltando
- 🚫 Alterar o histórico de estoque, apagar uma peça ou mudar sua etiqueta
- 🚫 Editar o código do produto à mão (ele é gerado pelo banco)

---

## 📁 Estrutura do Projeto

```
ItsJoias/
├── frontend/              telas (HTML, CSS e JavaScript), publicadas na Vercel
│   ├── login.html         entrada no sistema
│   ├── index.html         Painel (visualização geral)
│   ├── vendas.html        nova venda
│   ├── clientes.html      cadastro e lista de clientes
│   ├── maletas.html       maletas ativas, importação de maleta por PDF e histórico
│   ├── produtos.html      peças disponíveis
│   ├── css/style.css
│   └── js/                config.js (endereço da API), api.js (login e chamadas), uma por tela
├── backend/               API REST em Spring Boot, publicada no Render
│   ├── Dockerfile
│   └── src/main/java/br/com/itsjoias/
│       ├── controller/    rotas /api/...
│       ├── service/       regras de negócio (venda, painel, clientes)
│       ├── repository/    acesso ao banco
│       ├── dto/           formatos de entrada e saída
│       ├── seguranca/     login (Supabase Auth) e perfis
│       └── config/        segurança, CORS, relógio
└── database/              script completo do banco e alterações
```

---

## 🏗️ Arquitetura e publicação

```
Navegador ──► Vercel (frontend) ──► Render (backend Spring Boot) ──► Supabase (PostgreSQL + Auth)
```

| Peça | Onde roda | O que faz |
|------|-----------|-----------|
| **Frontend** | Vercel (`itsjoias.vercel.app`) | Só exibe e envia dados; nenhuma conta de dinheiro é feita no navegador |
| **Backend** | Render (Docker, Java 21) | Confere o login, aplica as regras e fala com o banco |
| **Banco** | Supabase (PostgreSQL) | Guarda os fatos; recusa o que nunca pode acontecer |
| **Login** | Supabase Auth | Guarda e-mail e senha; o ItsJoias nunca guarda senha |

**Segurança em camadas**
- O back confere a assinatura do token de login em cada requisição e o perfil de quem está usando.
- As tabelas do Supabase não aceitam acesso direto pela API pública (RLS ligado, sem políticas, e
  permissões dos papéis `anon`/`authenticated` retiradas): o único caminho até os dados é o back.
- O CORS só aceita o site da Vercel e a máquina local.

### Perfis

| Perfil | Pode |
|--------|------|
| **Proprietária** (Leide) | Tudo, inclusive excluir |
| **Desenvolvimento** (Teste) | Consultar, cadastrar e editar; não exclui |

As senhas são criadas no painel do Supabase (*Authentication → Users*); cada login é ligado a um
perfil na tabela `usuarios` (colunas `auth_user_id` e `perfil`).

### Variáveis de ambiente do backend

| Variável | Exemplo | Segredo? |
|----------|---------|----------|
| `DB_URL` | `jdbc:postgresql://<host-do-session-pooler>:5432/postgres?sslmode=require` | não |
| `DB_USERNAME` | `postgres.<ref-do-projeto>` | não |
| `DB_PASSWORD` | senha do banco no Supabase | **sim** |
| `SUPABASE_URL` | `https://<ref-do-projeto>.supabase.co` | não |
| `SUPABASE_PUBLISHABLE_KEY` | `sb_publishable_...` (chave pública) | não |
| `SUPABASE_JWT_SECRET` | só se o projeto usar o segredo JWT antigo (HS256) | **sim** |
| `CORS_ORIGENS` | opcional; padrão: Vercel + localhost | não |

Segredos ficam **só** no painel do Render (ou no seu computador), nunca no GitHub.
A chave `service_role` do Supabase não é usada.

---

## ▶️ Como executar na sua máquina

1. **Banco:** crie o banco `its_joias` no pgAdmin e rode `database/its_joias_banco.sql` (veja `database/README.md`).
2. **Backend** (pasta `backend`), com as variáveis da tabela acima definidas:
   ```bash
   ./mvnw spring-boot:run
   ```
   A API sobe em `http://localhost:8080`.
   Para a **importação de PDF** funcionar na sua máquina, instale também os programas gratuitos de leitura
   (no servidor eles já vêm no `Dockerfile`):
   - Linux: `sudo apt install poppler-utils tesseract-ocr tesseract-ocr-por`
   - Windows: instale o Poppler e o Tesseract (com o idioma português) e, se não estiverem no PATH,
     defina `OCR_POPPLER_DIR` (pasta `bin` do Poppler) e `OCR_TESSERACT` (caminho do `tesseract.exe`).
3. **Frontend:** abra a pasta `frontend` com um servidor simples, por exemplo:
   ```bash
   python3 -m http.server 5500
   ```
   e acesse `http://localhost:5500/login.html`. Na máquina local, o front chama `localhost:8080` sozinho.
4. **Testes** (pasta `backend`), com um banco de teste vazio (`its_joias_teste`):
   ```bash
   ./mvnw test
   ```

---

## 🗺️ Roadmap

| Etapa | Situação |
|-------|----------|
| Banco de dados completo (tabelas, views, triggers) | ✅ |
| Painel da vendedora | ✅ |
| Login e perfis (Supabase Auth) | ✅ |
| Clientes: cadastrar, listar, editar, desativar, excluir | ✅ |
| Nova venda (etiqueta ou modelo, à vista ou parcelado, entrada, +10%) | ✅ |
| Publicação: back no Render, front na Vercel | ✅ |
| Importação de maleta por PDF (digital ou escaneado, com conferência antes de salvar) | ✅ |
| Meu perfil (nome, e-mail, senha) | ⏳ próxima |
| Baixa/acerto da maleta antiga (o que voltou e o que ficou) | ⏳ |
| Registrar pagamentos depois da venda, estorno e cancelamento | ⏳ |
| Tema claro e escuro | ⏳ |
| Cobranças (fase 1.5) | ⏳ |
| Interface em React | ⏳ |

---

## 👩‍💻 Equipe

- **Desenvolvimento: Flavia Fernandes** ([@b0l4da](https://github.com/b0l4da))
- Maria Clara ([@mclarafl](https://github.com/mclarafl))

Projeto de estudo feito para a marca **Leide Farias**.
