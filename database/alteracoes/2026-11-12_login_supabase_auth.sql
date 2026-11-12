-- =====================================================================
-- ITS Joias — login pelo Supabase Auth e perfis de acesso
--
-- 1. usuarios passa a apontar o login do Supabase Auth (auth_user_id) e a ter
--    um perfil: PROPRIETARIA (tudo) ou DESENVOLVIMENTO (consulta, cria e edita;
--    não exclui). A senha fica SÓ no Supabase Auth: senha_hash deixa de ser
--    obrigatória (a coluna fica, sem uso, para não apagar nada).
-- 2. As tabelas e views deixam de aceitar acesso direto pela API pública do
--    Supabase (papéis anon e authenticated). O único caminho até os dados é o
--    back-end (Render), que confere o login e o perfil. Junto com o RLS ligado
--    e sem políticas, isso fecha a porta por dois lados.
--
-- Não apaga nem altera nenhum dado.
-- COMO RODAR: pgAdmin > Query Tool > executar o ARQUIVO INTEIRO (F5). Uma vez só.
-- No Supabase isto foi aplicado em 12/11/2026.
-- =====================================================================

BEGIN;

ALTER TABLE usuarios
    ADD COLUMN auth_user_id UUID,
    ADD COLUMN perfil VARCHAR(20) NOT NULL DEFAULT 'DESENVOLVIMENTO',
    ALTER COLUMN senha_hash DROP NOT NULL,
    ADD CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('PROPRIETARIA', 'DESENVOLVIMENTO'));

-- um login do Supabase liga a no máximo um usuário do ItsJoias
CREATE UNIQUE INDEX uq_usuarios_auth_user ON usuarios (auth_user_id) WHERE auth_user_id IS NOT NULL;

COMMENT ON COLUMN usuarios.auth_user_id IS 'id do login em auth.users (Supabase Auth)';
COMMENT ON COLUMN usuarios.senha_hash   IS 'sem uso: a senha fica no Supabase Auth';

-- Os papéis anon/authenticated só existem no Supabase; no banco local este bloco não faz nada.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated;
        REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES    FROM anon, authenticated;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
    END IF;
END $$;

COMMIT;
