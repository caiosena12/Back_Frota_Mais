package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.frotamais.comum.TentativasExcedidasException;
import br.com.frotamais.viagem.ApiViagemTeste;
import tools.jackson.databind.JsonNode;

/**
 * Limite de tentativas com a cadeia real (MockMvc, tokens reais), com
 * limites pequenos. Cada teste usa IPs e e-mails próprios: os contadores
 * ficam na memória do contexto e não são desfeitos pelo @Transactional.
 */
@TestPropertySource(properties = {
    "app.tentativas.ativo=true",
    "app.tentativas.login-ip.limite=5",
    "app.tentativas.login-conta.limite=3",
    "app.tentativas.registro-ip.limite=2",
    "app.tentativas.ativacao-ip.limite=3"
})
class LimiteTentativasTest extends ApiViagemTeste {

    private static String ipNovo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(1, 255) + "." + r.nextInt(1, 255) + "." + r.nextInt(1, 255);
    }

    private static RequestPostProcessor ip(String ip) {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    /** Registro de um IP próprio (o registrar() herdado usaria sempre o mesmo IP). */
    private record ContaIp(String token, String email) {
    }

    private ContaIp conta() throws Exception {
        String email = emailNovo();
        MvcResult r = enviar(post("/api/auth/registro").with(ip(ipNovo())), corpo("nomeEmpresa", "Empresa",
                "nome", "Dono", "email", email, "senha", "SenhaTeste123!"), 201);
        return new ContaIp(objectMapper.readTree(r.getResponse().getContentAsString()).get("token").asString(), email);
    }

    private static String emailNovo() {
        return "limite-" + UUID.randomUUID() + "@exemplo.test";
    }

    private MvcResult login(String ip, String email, String senha, int status) throws Exception {
        return enviar(post("/api/auth/login").with(ip(ip)), corpo("email", email, "senha", senha), status);
    }

    private MvcResult enviar(MockHttpServletRequestBuilder req, Object corpo, int status) throws Exception {
        MvcResult r = mockMvc.perform(req.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo))).andReturn();
        assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return r;
    }

    private void confere429(MvcResult r, long retryMaximo) throws Exception {
        JsonNode erro = objectMapper.readTree(r.getResponse().getContentAsString());
        assertEquals(429, erro.get("status").asInt());
        assertEquals("Muitas tentativas", erro.get("erro").asString());
        assertEquals(TentativasExcedidasException.MENSAGEM, erro.get("detalhes").get(0).asString());
        assertEquals(3, erro.size());
        long retry = Long.parseLong(r.getResponse().getHeader("Retry-After"));
        assertTrue(retry > 0 && retry <= retryMaximo, "Retry-After " + retry);
    }

    @Test
    void loginPorIpDentroDoLimiteEDepoisBloqueadoSemAfetarOutroIp() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 5; i++) {
            login(ip, emailNovo(), "SenhaErrada1", 401);
        }
        confere429(login(ip, emailNovo(), "SenhaErrada1", 429), 300);
        login(ipNovo(), emailNovo(), "SenhaErrada1", 401);
    }

    @Test
    void xForwardedForEnviadoPeloClienteNaoTrocaOIp() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 5; i++) {
            enviar(post("/api/auth/login").with(ip(ip)).header("X-Forwarded-For", ipNovo()),
                    corpo("email", emailNovo(), "senha", "SenhaErrada1"), 401);
        }
        enviar(post("/api/auth/login").with(ip(ip)).header("X-Forwarded-For", ipNovo())
                .header("X-Real-IP", ipNovo()), corpo("email", emailNovo(), "senha", "SenhaErrada1"), 429);
    }

    @Test
    void rotaComCodificacaoOuParametroNaoEscapaDoLimite() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 5; i++) {
            login(ip, emailNovo(), "SenhaErrada1", 401);
        }
        // %6C = "l": o Spring roteia para o login, e o limite vale.
        enviar(post(URI.create("/api/auth/%6Cogin")).with(ip(ip)), corpo("email", emailNovo(), "senha", "SenhaErrada1"), 429);
        // ";" e "//" nem chegam ao login: o firewall do Spring Security recusa (400).
        for (String rota : List.of("/api/auth/login;x=1", "/api/auth//login")) {
            MvcResult r = enviar(post(URI.create(rota)).with(ip(ipNovo())),
                    corpo("email", emailNovo(), "senha", "SenhaErrada1"), 400);
            assertEquals(null, r.getHandler(), rota);
        }
    }

    @Test
    void contaComFalhasDemaisBloqueiaMesmoComSenhaCertaEDeOutrosIps() throws Exception {
        ContaIp conta = conta();
        for (int i = 0; i < 3; i++) {
            login(ipNovo(), conta.email(), "SenhaErrada1", 401);
        }
        confere429(login(ipNovo(), conta.email(), "SenhaTeste123!", 429), 900);
        // Outra conta não é afetada.
        ContaIp outra = conta();
        login(ipNovo(), outra.email(), "SenhaTeste123!", 200);
        // Maiúsculas e espaços não criam uma chave nova.
        login(ipNovo(), conta.email().toUpperCase(), "SenhaTeste123!", 429);
    }

    @Test
    void bloqueioPorContaNaoRevelaSeOEmailExiste() throws Exception {
        ContaIp conta = conta();
        String inexistente = emailNovo();
        for (int i = 0; i < 3; i++) {
            login(ipNovo(), conta.email(), "SenhaErrada1", 401);
            login(ipNovo(), inexistente, "SenhaErrada1", 401);
        }
        MvcResult existe = login(ipNovo(), conta.email(), "SenhaErrada1", 429);
        MvcResult naoExiste = login(ipNovo(), inexistente, "SenhaErrada1", 429);
        assertEquals(existe.getResponse().getContentAsString(), naoExiste.getResponse().getContentAsString());
    }

    @Test
    void loginBemSucedidoZeraAsFalhasDaConta() throws Exception {
        ContaIp conta = conta();
        login(ipNovo(), conta.email(), "SenhaErrada1", 401);
        login(ipNovo(), conta.email(), "SenhaErrada1", 401);
        login(ipNovo(), conta.email(), "SenhaTeste123!", 200);
        login(ipNovo(), conta.email(), "SenhaErrada1", 401);
        login(ipNovo(), conta.email(), "SenhaErrada1", 401);
        login(ipNovo(), conta.email(), "SenhaTeste123!", 200);
    }

    @Test
    void registroPorIpBloqueiaAntesDeGravarEDeValidar() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 2; i++) {
            enviar(post("/api/auth/registro").with(ip(ip)), corpo("nomeEmpresa", "Empresa", "nome", "Dono",
                    "email", emailNovo(), "senha", "SenhaTeste123!"), 201);
        }
        String bloqueado = emailNovo();
        confere429(enviar(post("/api/auth/registro").with(ip(ip)), corpo("nomeEmpresa", "Empresa", "nome", "Dono",
                "email", bloqueado, "senha", "SenhaTeste123!"), 429), 3600);
        enviar(post("/api/auth/registro").with(ip(ip)), corpo(), 429);
        // Nada foi criado para o e-mail recusado.
        login(ipNovo(), bloqueado, "SenhaTeste123!", 401);
    }

    @Test
    void ativacaoDeConvitePorIp() throws Exception {
        String ip = ipNovo();
        for (int i = 0; i < 3; i++) {
            enviar(post("/api/convites/ativacao").with(ip(ip)),
                    corpo("token", "token-inventado-" + i, "senha", "Motorista123"), 400);
        }
        confere429(enviar(post("/api/convites/ativacao").with(ip(ip)),
                corpo("token", "token-inventado", "senha", "Motorista123"), 429), 900);
        enviar(post("/api/convites/ativacao").with(ip(ipNovo())),
                corpo("token", "token-inventado", "senha", "Motorista123"), 400);
    }

    @Test
    void rotasAutenticadasNaoSaoContadas() throws Exception {
        String ip = ipNovo();
        ContaIp conta = conta();
        for (int i = 0; i < 8; i++) {
            enviar(post("/api/auth/senha").with(ip(ip)).header("Authorization", "Bearer " + conta.token()),
                    corpo("senhaAtual", "Errada123", "novaSenha", "NovaSenha123"), 400);
        }
    }
}
