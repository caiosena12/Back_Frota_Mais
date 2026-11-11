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
 * Fretes.
 *
 * DONO: tudo. MOTORISTA: só viagens de caminhões vinculados a ele (as demais
 * respondem 404); abre, altera enquanto em andamento, lança e finaliza; não
 * vê custos, lucro, margem nem indicadores; não cadastra viagem histórica
 * nem cancela viagem.
 * Essas regras de dados ficam nos serviços (ViagemService, AcessoCaminhao);
 * aqui só a separação por perfil.
 */
@RestController
@RequestMapping("/api/viagens")
@PreAuthorize("hasAnyRole('DONO', 'MOTORISTA')")
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
    @PreAuthorize("hasRole('DONO')")
    public IndicadoresResponse indicadores(
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Long caminhaoId) {
        return indicadores.indicadores(ano, mes, semana, de, ate, caminhaoId);
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

    /** Frete esquecido, cadastrado já finalizado, com seus lançamentos. Só DONO. */
    @PostMapping("/historicas")
    @PreAuthorize("hasRole('DONO')")
    @ResponseStatus(HttpStatus.CREATED)
    public ViagemResponse criarHistorica(@Valid @RequestBody ViagemHistoricaRequest request) {
        return service.criarHistorica(request);
    }

    @PutMapping("/{id}")
    public ViagemResponse atualizar(@PathVariable Long id, @Valid @RequestBody ViagemAtualizacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/finalizar")
    public ViagemResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizacaoRequest request) {
        return service.finalizar(id, request);
    }

    /** Cancela uma viagem em andamento, com motivo obrigatório. Só DONO. */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('DONO')")
    public ViagemResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelamentoRequest request) {
        return service.cancelar(id, request);
    }
}
