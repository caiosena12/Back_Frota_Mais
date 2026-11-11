package br.com.frotamais.seguranca;

import br.com.frotamais.usuario.Perfil;

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

    /**
     * Perfil vindo das authorities, que o conversor de SecurityConfig carrega do
     * banco a cada requisição. A claim "perfil" do token nunca é usada para isso.
     */
    public boolean isDono() {
        return possui("ROLE_" + Perfil.DONO.name());
    }

    public boolean isMotorista() {
        return possui("ROLE_" + Perfil.MOTORISTA.name());
    }

    private boolean possui(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private Jwt jwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("Não há autenticação JWT válida.");
        }
        return jwtAuth.getToken();
    }
}
