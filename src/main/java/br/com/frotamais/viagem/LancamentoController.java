package br.com.frotamais.viagem;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Lançamentos do frete (menu "Ações"). Mesmo controle de acesso de ViagemController. */
@RestController
@RequestMapping("/api/viagens/{viagemId}")
@PreAuthorize("hasRole('DONO')")
public class LancamentoController {

    private final LancamentoService service;

    public LancamentoController(LancamentoService service) {
        this.service = service;
    }

    @PostMapping("/abastecimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AbastecimentoResponse adicionarAbastecimento(@PathVariable Long viagemId,
            @Valid @RequestBody AbastecimentoRequest request) {
        return service.adicionarAbastecimento(viagemId, request);
    }

    @PutMapping("/abastecimentos/{id}")
    public AbastecimentoResponse atualizarAbastecimento(@PathVariable Long viagemId, @PathVariable Long id,
            @Valid @RequestBody AbastecimentoRequest request) {
        return service.atualizarAbastecimento(viagemId, id, request);
    }

    @DeleteMapping("/abastecimentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirAbastecimento(@PathVariable Long viagemId, @PathVariable Long id) {
        service.excluirAbastecimento(viagemId, id);
    }

    @PostMapping("/pedagios")
    @ResponseStatus(HttpStatus.CREATED)
    public PedagioResponse adicionarPedagio(@PathVariable Long viagemId,
            @Valid @RequestBody PedagioRequest request) {
        return service.adicionarPedagio(viagemId, request);
    }

    @PutMapping("/pedagios/{id}")
    public PedagioResponse atualizarPedagio(@PathVariable Long viagemId, @PathVariable Long id,
            @Valid @RequestBody PedagioRequest request) {
        return service.atualizarPedagio(viagemId, id, request);
    }

    @DeleteMapping("/pedagios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirPedagio(@PathVariable Long viagemId, @PathVariable Long id) {
        service.excluirPedagio(viagemId, id);
    }

    @PostMapping("/despesas-extras")
    @ResponseStatus(HttpStatus.CREATED)
    public DespesaExtraResponse adicionarDespesaExtra(@PathVariable Long viagemId,
            @Valid @RequestBody DespesaExtraRequest request) {
        return service.adicionarDespesaExtra(viagemId, request);
    }

    @PutMapping("/despesas-extras/{id}")
    public DespesaExtraResponse atualizarDespesaExtra(@PathVariable Long viagemId, @PathVariable Long id,
            @Valid @RequestBody DespesaExtraRequest request) {
        return service.atualizarDespesaExtra(viagemId, id, request);
    }

    @DeleteMapping("/despesas-extras/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirDespesaExtra(@PathVariable Long viagemId, @PathVariable Long id) {
        service.excluirDespesaExtra(viagemId, id);
    }
}
