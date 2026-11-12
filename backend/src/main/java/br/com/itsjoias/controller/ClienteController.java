package br.com.itsjoias.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.itsjoias.dto.ClienteDTO.ClienteForm;
import br.com.itsjoias.dto.ClienteDTO.ClienteResposta;
import br.com.itsjoias.service.ClienteService;
import jakarta.validation.Valid;

/**
 * Clientes.
 * GET    /api/clientes?busca=ana&incluirInativos=false   lista (com saldo em aberto)
 * GET    /api/clientes/{id}                              uma cliente
 * POST   /api/clientes                                   cadastra
 * PUT    /api/clientes/{id}                              edita (inclusive ativa/desativa)
 * DELETE /api/clientes/{id}                              exclui, se não tiver histórico (só PROPRIETARIA)
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService clienteService;

	public ClienteController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping
	public List<ClienteResposta> listar(@RequestParam(required = false) String busca,
			@RequestParam(defaultValue = "false") boolean incluirInativos) {
		return clienteService.listar(busca, incluirInativos);
	}

	@GetMapping("/{id}")
	public ClienteResposta buscar(@PathVariable long id) {
		return clienteService.buscar(id);
	}

	@PostMapping
	public ResponseEntity<ClienteResposta> criar(@Valid @RequestBody ClienteForm form) {
		return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.criar(form));
	}

	@PutMapping("/{id}")
	public ClienteResposta editar(@PathVariable long id, @Valid @RequestBody ClienteForm form) {
		return clienteService.editar(id, form);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> excluir(@PathVariable long id) {
		clienteService.excluir(id);
		return ResponseEntity.noContent().build();
	}

}
