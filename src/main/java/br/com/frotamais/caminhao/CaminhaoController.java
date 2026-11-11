package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/caminhoes")
public class CaminhaoController {

    private final CaminhaoService service;

    public CaminhaoController(CaminhaoService service) {
        this.service = service;
    }

    /**
     * DONO: caminhões da empresa; excluídos só com incluirExcluidos=true.
     * MOTORISTA: só os vinculados a ele (o parâmetro é ignorado).
     */
    @GetMapping
    public List<CaminhaoResponse> listar(@RequestParam(defaultValue = "false") boolean incluirExcluidos) {
        return service.listar(incluirExcluidos);
    }

    @GetMapping("/{id}")
    public CaminhaoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DONO')")
    public CaminhaoResponse criar(@Valid @RequestBody CaminhaoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DONO')")
    public CaminhaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CaminhaoRequest request) {
        return service.atualizar(id, request);
    }

    /** Exclusão lógica: o caminhão sai da operação, o histórico fica. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DONO')")
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
