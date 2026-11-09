-- =====================================================================
-- ITS Joias — correções de segurança apontadas pelo Supabase (Security Advisor)
--
-- 1. Views passam a usar "security_invoker": elas respeitam as permissões e o
--    RLS de quem consulta, e não de quem criou a view. Sem isso, qualquer pessoa
--    com a chave pública do Supabase conseguiria ler as views pela API,
--    passando por cima do RLS das tabelas.
-- 2. Funções passam a ter o search_path fixo (public, pg_temp): elas sempre
--    procuram as tabelas no schema public, e ninguém consegue "trocar" a tabela
--    que a função enxerga criando outra com o mesmo nome em outro schema.
--
-- Nada muda para o Spring: ele conecta como dono das tabelas e continua vendo tudo.
-- Nenhum dado é alterado.
--
-- No banco do Supabase isto JÁ FOI APLICADO em 09/11/2026.
-- COMO RODAR no seu banco local: no pgAdmin, conectado no banco its_joias,
-- abrir este arquivo na Query Tool e executar o ARQUIVO INTEIRO (F5).
-- Pode rodar mais de uma vez sem problema.
-- Depois disso, o its_joias_banco.sql do repositório já tem a mesma mudança.
-- =====================================================================

BEGIN;

-- 1. Views
ALTER VIEW vw_busca_codigo         SET (security_invoker = on);
ALTER VIEW vw_busca_peca           SET (security_invoker = on);
ALTER VIEW vw_dashboard_maletas    SET (security_invoker = on);
ALTER VIEW vw_divergencia_estoque  SET (security_invoker = on);
ALTER VIEW vw_estoque_maleta_itens SET (security_invoker = on);
ALTER VIEW vw_pecas                SET (security_invoker = on);
ALTER VIEW vw_produtos_estoque     SET (security_invoker = on);
ALTER VIEW vw_resumo_financeiro    SET (security_invoker = on);
ALTER VIEW vw_saldo_clientes       SET (security_invoker = on);
ALTER VIEW vw_saldo_parcelas       SET (security_invoker = on);

-- 2. Funções
ALTER FUNCTION fn_bloquear_alteracao()        SET search_path = public, pg_temp;
ALTER FUNCTION fn_categorias_protecao()       SET search_path = public, pg_temp;
ALTER FUNCTION fn_mov_validar()               SET search_path = public, pg_temp;
ALTER FUNCTION fn_pagamentos_protecao()       SET search_path = public, pg_temp;
ALTER FUNCTION fn_parcelas_protecao()         SET search_path = public, pg_temp;
ALTER FUNCTION fn_pecas_protecao()            SET search_path = public, pg_temp;
ALTER FUNCTION fn_produtos_codigo()           SET search_path = public, pg_temp;
ALTER FUNCTION fn_set_atualizado_em()         SET search_path = public, pg_temp;
ALTER FUNCTION fn_trg_conferir_maleta_item()  SET search_path = public, pg_temp;
ALTER FUNCTION fn_trg_validar_venda()         SET search_path = public, pg_temp;
ALTER FUNCTION fn_validar_venda(BIGINT)       SET search_path = public, pg_temp;
ALTER FUNCTION fn_vendas_protecao()           SET search_path = public, pg_temp;

COMMIT;
