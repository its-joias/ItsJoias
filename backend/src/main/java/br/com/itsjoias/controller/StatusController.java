package br.com.itsjoias.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET / : o Render usa para saber se o back está no ar (não precisa de login). */
@RestController
public class StatusController {

	@GetMapping("/")
	public Map<String, String> status() {
		return Map.of("status", "ok", "sistema", "ItsJoias");
	}

}
