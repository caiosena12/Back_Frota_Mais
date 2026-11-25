package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * IP real atrás de proxy, com o Tomcat de verdade (porta aleatória) e
 * server.forward-headers-strategy=native, como no perfil prod. O teste
 * conecta de 127.0.0.1, que o Tomcat trata como proxy confiável: vale o
 * primeiro IP não confiável do X-Forwarded-For, da direita para a esquerda.
 *
 * Só faz logins com e-mails inexistentes: não grava nada no banco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "server.forward-headers-strategy=native",
    "app.tentativas.ativo=true",
    "app.tentativas.login-ip.limite=3"
})
class LimiteTentativasProxyTest {

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private int login(String xForwardedFor) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"proxy-" + UUID.randomUUID()
                        + "@exemplo.test\",\"senha\":\"SenhaErrada1\"}"));
        if (xForwardedFor != null) {
            req.header("X-Forwarded-For", xForwardedFor);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void clienteIdentificadoPeloProxyConfiavel() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(401, login("203.0.113.10"));
        }
        assertEquals(429, login("203.0.113.10"));
        assertEquals(401, login("203.0.113.11"), "outro cliente atrás do mesmo proxy não é afetado");
    }

    @Test
    void prefixoForjadoPeloClienteNaoTrocaOIp() throws Exception {
        // O cliente escreve qualquer coisa à esquerda; o proxy acrescenta o IP real.
        assertEquals(401, login("1.1.1.1, 203.0.113.20"));
        assertEquals(401, login("8.8.8.8, 203.0.113.20"));
        assertEquals(401, login("10.9.9.9, 203.0.113.20"));
        assertEquals(429, login("4.4.4.4, 203.0.113.20"));
    }
}
