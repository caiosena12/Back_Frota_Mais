package br.com.frotamais.viagem;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * Fretes. Acesso restrito ao DONO.
 *
 * O MOTORISTA só pode ver e lançar dados dos caminhões vinculados a ele, e
 * esse vínculo ainda não existe (Anexo A, P12). Até existir, liberar o
 * MOTORISTA daria a ele acesso a todos os caminhões da empresa; por isso o
 * acesso fica negado (403).
 */
@RestController
@RequestMapping("/api/viagens")
@PreAuthorize("hasRole('DONO')")
public class ViagemController {

    private final ViagemService service;
    private final IndicadorService indicadores;

    public ViagemController(ViagemService service, IndicadorService indicadores) {
        this.service = service;
        this.indicadores = indicadores;
    }

    @GetMapping
    public List<ViagemResumoResponse> listar(
            @RequestParam(required = false) StatusViagem status,
            @RequestParam(required = false) Long caminhaoId) {
        return service.listar(status, caminhaoId);
    }

    @GetMapping("/indicadores")
    public IndicadoresResponse indicadores(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long caminhaoId) {
        return indicadores.indicadores(ano, mes, de, ate, caminhaoId);
    }

    @GetMapping("/{id}")
    public ViagemResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ViagemResponse criar(@Valid @RequestBody ViagemCriacaoRequest request) {
        return service.criar(request);
    }

    @PutMapping("/{id}")
    public ViagemResponse atualizar(@PathVariable Long id, @Valid @RequestBody ViagemAtualizacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/finalizar")
    public ViagemResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizacaoRequest request) {
        return service.finalizar(id, request);
    }
}
