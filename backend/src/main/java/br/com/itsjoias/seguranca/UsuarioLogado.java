package br.com.itsjoias.seguranca;

/** Quem está usando o sistema agora (linha de public.usuarios ligada ao login do Supabase). */
public record UsuarioLogado(long id, String nome, String email, Perfil perfil) {
}
