package br.com.frotamais.comum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 e 403 decididos pelos filtros de segurança (antes do controller), no
 * mesmo formato JSON dos demais erros. Os handlers padrão do resource server
 * continuam rodando antes, para manter o cabeçalho WWW-Authenticate.
 */
public final class RespostasSeguranca {

    private RespostasSeguranca() {
    }

    public static AuthenticationEntryPoint naoAutenticado() {
        BearerTokenAuthenticationEntryPoint padrao = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            padrao.commence(request, response, ex);
            escrever(response, RespostaErro.de(HttpStatus.UNAUTHORIZED,
                    "Autenticação necessária: envie um token válido e não expirado."));
        };
    }

    public static AccessDeniedHandler acessoNegado() {
        BearerTokenAccessDeniedHandler padrao = new BearerTokenAccessDeniedHandler();
        return (request, response, ex) -> {
            padrao.handle(request, response, ex);
            escrever(response, RespostaErro.de(HttpStatus.FORBIDDEN,
                    "Seu perfil não tem permissão para esta operação."));
        };
    }

    private static void escrever(HttpServletResponse response, RespostaErro erro) throws IOException {
        response.setStatus(erro.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(erro.json());
    }
}
