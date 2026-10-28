package br.com.frotamais.seguranca;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class UsuarioLogado {

    public Long empresaId() {
        Number id = jwt().getClaim("empresaId");
        if (id == null) {
            throw new IllegalStateException("O token não contém empresaId.");
        }
        return id.longValue();
    }

    public Long usuarioId() {
        return Long.valueOf(jwt().getSubject());
    }

    private Jwt jwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("Não há autenticação JWT válida.");
        }
        return jwtAuth.getToken();
    }
}
