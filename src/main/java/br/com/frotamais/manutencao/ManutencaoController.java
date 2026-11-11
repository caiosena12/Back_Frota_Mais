package br.com.frotamais.manutencao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Manutenções dos caminhões: informação financeira, só DONO. */
@RestController
@RequestMapping("/api/manutencoes")
@PreAuthorize("hasRole('DONO')")
public class ManutencaoController {

    private final ManutencaoService service;

    public ManutencaoController(ManutencaoService service) {
        this.service = service;
    }

    /** Filtros opcionais por caminhão e período (pela data). Excluídas só com incluirExcluidas=true. */
    @GetMapping
    public List<ManutencaoResponse> listar(
            @RequestParam(required = false) Long caminhaoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "false") boolean incluirExcluidas) {
        return service.listar(caminhaoId, de, ate, incluirExcluidas);
    }

    @GetMapping("/{id}")
    public ManutencaoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManutencaoResponse criar(@Valid @RequestBody ManutencaoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public ManutencaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ManutencaoRequest request) {
        return service.atualizar(id, request);
    }

    /** Exclusão lógica: sai das listas e dos totais, o registro fica. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
