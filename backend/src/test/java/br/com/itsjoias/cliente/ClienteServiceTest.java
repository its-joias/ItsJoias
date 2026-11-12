package br.com.itsjoias.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.com.itsjoias.dto.ClienteDTO.ClienteForm;
import br.com.itsjoias.dto.ClienteDTO.ClienteResposta;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.exception.TradutorErroBanco;
import br.com.itsjoias.repository.ClienteConsultaRepository;
import br.com.itsjoias.service.ClienteService;

/** Clientes contra o Postgres de teste: grava, lista com saldo, edita, exclui e recusa o que não pode. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("teste")
@Import({ ClienteService.class, ClienteConsultaRepository.class })
class ClienteServiceTest {

	@Autowired
	private ClienteService clientes;

	@Autowired
	private JdbcTemplate jdbc;

	private static ClienteForm form(String nome, String telefone, String cpf) {
		return new ClienteForm(nome, telefone, null, cpf, null, null, null);
	}

	@Test
	void cadastroAparecaNaListagemComSaldoZerado() {
		ClienteResposta criada = clientes.criar(new ClienteForm("  Maria Teste  ", "(71) 98888-7777", " MARIA@Exemplo.com ",
				"529.982.247-25", "", null, null));

		assertThat(criada.nome()).isEqualTo("Maria Teste");
		assertThat(criada.email()).isEqualTo("maria@exemplo.com");
		assertThat(criada.endereco()).isNull();
		assertThat(criada.ativo()).isTrue();
		assertThat(criada.totalEmAberto()).isEqualTo(new BigDecimal("0.00"));
		assertThat(clientes.listar("maria", false)).extracting(ClienteResposta::id).contains(criada.id());
		assertThat(clientes.listar("98888", false)).extracting(ClienteResposta::id).contains(criada.id());
	}

	@Test
	void edicaoEDesativacaoSaoPersistidas() {
		long id = clientes.criar(form("Bia Teste", "(71) 97777-6666", null)).id();

		clientes.editar(id, new ClienteForm("Beatriz Teste", "(71) 3333-4444", null, null, "Rua A, 1", null, false));

		ClienteResposta b = clientes.buscar(id);
		assertThat(b.nome()).isEqualTo("Beatriz Teste");
		assertThat(b.telefone()).isEqualTo("(71) 3333-4444");
		assertThat(b.ativo()).isFalse();
		assertThat(clientes.listar("beatriz", false)).isEmpty();
		assertThat(clientes.listar("beatriz", true)).hasSize(1);
	}

	@Test
	void cpfComDigitoErradoERepetidoSaoRecusados() {
		assertThatThrownBy(() -> clientes.criar(form("X", "(71) 90000-0000", "123.456.789-00")))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("dígitos verificadores");

		clientes.criar(form("Primeira", "(71) 90000-0001", "529.982.247-25"));
		assertThatThrownBy(() -> clientes.criar(form("Segunda", "(71) 90000-0002", "529.982.247-25")))
				.satisfies(e -> assertThat(TradutorErroBanco.mensagem(e)).hasValue("Já existe uma cliente cadastrada com este CPF."));
	}

	@Test
	void excluiSemHistoricoMasNaoComVenda() {
		long semHistorico = clientes.criar(form("Sem Compras", "(71) 90000-0003", null)).id();
		clientes.excluir(semHistorico);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id = ?", Integer.class, semHistorico)).isZero();

		long comVenda = clientes.criar(form("Com Compras", "(71) 90000-0004", null)).id();
		long usuario = jdbc.queryForObject("INSERT INTO usuarios (nome, email) VALUES ('U', 'u@example.com') RETURNING id", Long.class);
		jdbc.update("INSERT INTO vendas (cliente_id, usuario_id) VALUES (?, ?)", comVenda, usuario); // rascunho já conta como histórico
		assertThatThrownBy(() -> clientes.excluir(comVenda))
				.isInstanceOf(RegraNegocioException.class).hasMessageContaining("desativá-la");
	}

}
