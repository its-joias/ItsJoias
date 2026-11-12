# 🗄️ Banco de dados — ITS Joias

PostgreSQL 18, administrado pelo pgAdmin 4. O banco é criado e alterado **à mão**: o Spring só confere se
as entidades batem com ele (`spring.jpa.hibernate.ddl-auto=validate`) e nunca cria nem altera tabelas.

| Arquivo | O que é |
|---------|---------|
| `its_joias_banco.sql` | Script **completo**: cria o banco inteiro do zero (tabelas, views, triggers e dados iniciais) |
| `alteracoes/` | Mudanças feitas depois, uma por arquivo, para quem já tem o banco criado (pasta criada junto com a primeira alteração) |

---

## Criar o banco do zero

1. No pgAdmin, crie um banco vazio chamado **`its_joias`**.
2. Conecte nele e abra `its_joias_banco.sql` na **Query Tool**.
3. Execute o **arquivo inteiro** (F5), sem selecionar trecho. A ordem importa: as tabelas dependem umas das outras.
4. Clique com o botão direito em *Tables* → **Refresh**.

> ⚠️ Se o banco já tinha tabelas, apague tudo antes. **Isso apaga todos os dados:**
> ```sql
> DROP SCHEMA public CASCADE;
> CREATE SCHEMA public;
> ```

## Conferir

```sql
SELECT (SELECT count(*) FROM information_schema.tables
         WHERE table_schema = 'public' AND table_type = 'BASE TABLE')   AS tabelas,
       (SELECT count(*) FROM information_schema.views
         WHERE table_schema = 'public')                                  AS views,
       (SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal)          AS triggers,
       (SELECT count(*) FROM categorias)                                 AS categorias,
       (SELECT count(*) FROM categoria_palavras_chave)                   AS palavras_chave;
```

Resultado esperado: **17 | 10 | 21 | 8 | 30**.

---

## Registrar uma mudança no banco

1. Crie um arquivo em `alteracoes/` com o nome `AAAA-MM-DD_descricao.sql`
   (ex.: `2026-11-03_view_acerto_fornecedor.sql`), só com os comandos da mudança (`ALTER TABLE`, `CREATE VIEW`...).
   Se for mais de um comando, coloque tudo entre `BEGIN;` e `COMMIT;`: se algo falhar, nada é aplicado.
2. Teste o arquivo num banco seu antes de abrir o PR.
3. Aplique a mesma mudança no `its_joias_banco.sql`, para ele continuar criando o banco completo.
4. No pull request, avise a outra pessoa para rodar o arquivo de alteração no banco dela.

**Nunca** altere o banco só pelo pgAdmin sem registrar o script: a outra pessoa fica com um banco diferente
e o Spring para de subir.

---

## Conectar o Spring ao Supabase (pelo Codespaces)

O `application.properties` lê três variáveis de ambiente. No Codespaces, elas são cadastradas como
**secrets**: no GitHub, *Settings → Codespaces → Secrets → New secret*, liberando para o repositório
`itsjoias` em *Repository access*.

| Secret | Valor |
|--------|-------|
| `DB_URL` | `jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.<ref-do-projeto>` (no pooler, o usuário leva o ref do projeto no final) |
| `DB_PASSWORD` | a senha do banco do projeto no Supabase |

- O **host** sai de *Connect → Session pooler* no painel do Supabase. O projeto está na região
  **us-east-2**, então o host tem `us-east-2` no nome. O Supabase mostra a URL como `postgresql://...`:
  troque o começo por `jdbc:postgresql://`.
- Use o **Session pooler**, não o Transaction pooler: o `SET TIME ZONE` do `application.properties`
  só se mantém no modo sessão.
- Depois de criar ou mudar um secret, **reinicie o Codespace** (os secrets só carregam quando ele inicia).
- Esqueceu a senha? *Project Settings → Database → Reset database password*, e atualize o secret.
  Quem mais usa o banco também precisa atualizar.

Para conferir se os secrets carregaram (mostra só se existem, sem mostrar a senha):

```bash
echo "URL: ${DB_URL:-FALTANDO}"; echo "Usuário: ${DB_USERNAME:-FALTANDO}"; [ -n "$DB_PASSWORD" ] && echo "Senha: ok" || echo "Senha: FALTANDO"
```

Erros mais comuns ao rodar `./mvnw spring-boot:run` na pasta `backend`:

| Mensagem | Causa |
|----------|-------|
| `Connection refused` com `localhost` | os secrets não carregaram (falta reiniciar o Codespace) |
| `Tenant or user not found` | host com região errada, ou usuário sem o `.<ref-do-projeto>` no final |
| `password authentication failed` | senha errada, ou o secret não foi atualizado depois de trocar a senha |
| `UnknownHostException` | erro de digitação no host |

---

## O que nunca vai para o repositório

- Backups e dumps do banco (`.backup`, `.dump`), com ou sem dados.
- Dados reais de clientes. Dados de teste são sempre fictícios.
- Senha do banco. Ela fica só na configuração local de cada uma (fora do Git).
