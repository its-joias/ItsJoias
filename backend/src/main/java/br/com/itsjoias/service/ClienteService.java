package br.com.itsjoias.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.itsjoias.dto.ClienteDTO.ClienteForm;
import br.com.itsjoias.dto.ClienteDTO.ClienteResposta;
import br.com.itsjoias.entity.Cliente;
import br.com.itsjoias.exception.NaoEncontradoException;
import br.com.itsjoias.exception.RegraNegocioException;
import br.com.itsjoias.repository.ClienteConsultaRepository;
import br.com.itsjoias.repository.ClienteRepository;

@Service
public class ClienteService {

	private final ClienteRepository clientes;
	private final ClienteConsultaRepository consulta;

	public ClienteService(ClienteRepository clientes, ClienteConsultaRepository consulta) {
		this.clientes = clientes;
		this.consulta = consulta;
	}

	@Transactional(readOnly = true)
	public List<ClienteResposta> listar(String busca, boolean incluirInativos) {
		return consulta.listar(busca, incluirInativos);
	}

	@Transactional(readOnly = true)
	public ClienteResposta buscar(long id) {
		return consulta.buscar(id).orElseThrow(() -> new NaoEncontradoException("Cliente não encontrada."));
	}

	@Transactional
	public ClienteResposta criar(ClienteForm form) {
		ClienteForm f = validarCpf(form.normalizado());
		Cliente cliente = new Cliente();
		copiar(f, cliente);
		cliente.setAtivo(true);
		long id = clientes.saveAndFlush(cliente).getId();
		return buscar(id);
	}

	@Transactional
	public ClienteResposta editar(long id, ClienteForm form) {
		ClienteForm f = validarCpf(form.normalizado());
		Cliente cliente = clientes.findById(id).orElseThrow(() -> new NaoEncontradoException("Cliente não encontrada."));
		copiar(f, cliente);
		if (f.ativo() != null) {
			cliente.setAtivo(f.ativo());
		}
		clientes.saveAndFlush(cliente);
		return buscar(id);
	}

	/** Só exclui quem não tem histórico. Quem já comprou fica guardada (pode ser desativada). */
	@Transactional
	public void excluir(long id) {
		if (!clientes.existsById(id)) {
			throw new NaoEncontradoException("Cliente não encontrada.");
		}
		if (consulta.temHistorico(id)) {
			throw new RegraNegocioException(
					"Esta cliente tem vendas ou cobranças registradas e não pode ser excluída. Você pode desativá-la.");
		}
		clientes.deleteById(id);
		clientes.flush();
	}

	private static void copiar(ClienteForm f, Cliente c) {
		c.setNome(f.nome());
		c.setTelefone(f.telefone());
		c.setEmail(f.email());
		c.setCpf(f.cpf());
		c.setEndereco(f.endereco());
		c.setObservacoes(f.observacoes());
	}

	/** Confere os dígitos verificadores do CPF (o formato já foi checado no formulário). */
	private static ClienteForm validarCpf(ClienteForm f) {
		if (f.cpf() != null && !cpfValido(f.cpf())) {
			throw new RegraNegocioException("CPF inválido: os dígitos verificadores não conferem.");
		}
		return f;
	}

	static boolean cpfValido(String cpf) {
		String d = cpf.replaceAll("\\D", "");
		if (d.length() != 11 || d.chars().distinct().count() == 1) {
			return false;
		}
		for (int pos = 9; pos <= 10; pos++) {
			int soma = 0;
			for (int i = 0; i < pos; i++) {
				soma += (d.charAt(i) - '0') * (pos + 1 - i);
			}
			int digito = (soma * 10) % 11 % 10;
			if (digito != d.charAt(pos) - '0') {
				return false;
			}
		}
		return true;
	}

}
