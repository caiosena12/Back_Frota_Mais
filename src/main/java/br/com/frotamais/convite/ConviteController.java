package br.com.frotamais.convite;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.frotamais.auth.TokenResponse;
import jakarta.validation.Valid;

/**
 * Rota pública: o convidado ainda não tem senha. O token do convite é a
 * credencial; vai no corpo (não na URL), para não aparecer em logs de acesso.
 */
@RestController
@RequestMapping("/api/convites")
public class ConviteController {

    private final ConviteService service;

    public ConviteController(ConviteService service) {
        this.service = service;
    }

    /** Define a senha, ativa a conta e devolve o token de acesso. */
    @PostMapping("/ativacao")
    public TokenResponse ativar(@Valid @RequestBody AtivacaoRequest request) {
        return service.ativar(request.token(), request.senha());
    }
}
