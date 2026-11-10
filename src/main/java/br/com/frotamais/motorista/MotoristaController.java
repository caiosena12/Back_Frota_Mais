package br.com.frotamais.motorista;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Gestão de motoristas: só o DONO. */
@RestController
@RequestMapping("/api/motoristas")
@PreAuthorize("hasRole('DONO')")
public class MotoristaController {

    private final MotoristaService service;

    public MotoristaController(MotoristaService service) {
        this.service = service;
    }

    @GetMapping
    public List<MotoristaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public MotoristaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    /** Cadastra o motorista e envia o convite por e-mail (503 se o e-mail não estiver configurado). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MotoristaResponse criar(@Valid @RequestBody MotoristaRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public MotoristaResponse atualizar(@PathVariable Long id, @Valid @RequestBody MotoristaAtualizacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PutMapping("/{id}/caminhoes")
    public MotoristaResponse definirCaminhoes(@PathVariable Long id, @Valid @RequestBody VinculosRequest request) {
        return service.definirCaminhoes(id, request);
    }

    @PostMapping("/{id}/convite")
    public MotoristaResponse reenviarConvite(@PathVariable Long id) {
        return service.reenviarConvite(id);
    }
}
