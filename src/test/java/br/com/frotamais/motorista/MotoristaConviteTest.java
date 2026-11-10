package br.com.frotamais.motorista;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.frotamais.suporte.CaixaDeSaidaTeste;
import br.com.frotamais.viagem.ApiViagemTeste;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;

/**
 * Cadastro de motorista por convite, ativação pelo próprio motorista, vínculo
 * com caminhões e isolamento, sempre com tokens reais (filtro de segurança
 * de verdade, não o jwt() do spring-security-test).
 */
class MotoristaConviteTest extends ApiViagemTeste {

    private static final String MOTORISTAS = "/api/motoristas";

    @Autowired
    private CaixaDeSaidaTeste caixa;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    private String dono;
    private long caminhaoA;
    private long caminhaoB;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoA = criarCaminhao(dono);
        caminhaoB = criarCaminhao(dono);
    }

    @AfterEach
    void restaurar() {
        caixa.simularIndisponivel(false);
    }

    private static String emailNovo() {
        return "motorista-" + UUID.randomUUID() + "@teste.com";
    }

    private JsonNode convidar(String email, Long... caminhoes) throws Exception {
        return chamar(POST(MOTORISTAS), dono, corpo("nome", "Motorista Teste", "email", email,
                "caminhaoIds", List.of(caminhoes)), 201);
    }

    private JsonNode ativar(String token, String senha, int status) throws Exception {
        return chamar(POST("/api/convites/ativacao"), null, corpo("token", token, "senha", senha), status);
    }

    /** Convida e ativa; devolve o token de acesso do motorista. */
    private String motoristaAtivo(String email, Long... caminhoes) throws Exception {
        convidar(email, caminhoes);
        return ativar(caixa.ultimoToken(email), "Motorista123", 200).get("token").asString();
    }

    private List<Long> idsListados(String token) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (JsonNode c : chamar(GET("/api/caminhoes"), token, null, 200)) {
            ids.add(c.get("id").asLong());
        }
        return ids;
    }

    private static void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void donoCadastraMotoristaSemSenhaEOConviteEEnviado() throws Exception {
        String email = emailNovo();
        JsonNode m = convidar(email, caminhaoA);

        assertEquals("CONVITE_PENDENTE", m.get("situacao").asString());
        assertEquals(List.of(caminhaoA), List.of(m.get("caminhaoIds").get(0).asLong()));
        assertFalse(m.get("conviteExpiraEm").isNull());
        assertFalse(m.toString().contains("token"), "o token nunca vai na resposta da API");

        assertEquals(1, caixa.paraEndereco(email).size());
        String texto = caixa.paraEndereco(email).get(0).texto();
        assertTrue(texto.contains("https://app.frotamais.teste/ativar?token="), texto);
        assertFalse(texto.toLowerCase().contains("senha:"), "o e-mail não leva senha");

        // O banco guarda só o hash do token.
        String token = caixa.ultimoToken(email);
        em.flush();
        List<String> hashes = jdbc.queryForList(
                "select token_hash from convites where usuario_id = ?", String.class, m.get("id").asLong());
        assertEquals(1, hashes.size());
        assertNotEquals(token, hashes.get(0));
        assertEquals(64, hashes.get(0).length());
    }

    @Test
    void motoristaCriaASenhaEPassaAVerSoOsCaminhoesVinculados() throws Exception {
        String email = emailNovo();
        String token = motoristaAtivo(email, caminhaoA);

        assertEquals(List.of(caminhaoA), idsListados(token));
        chamar(GET("/api/caminhoes/" + caminhaoA), token, null, 200);
        chamar(GET("/api/caminhoes/" + caminhaoB), token, null, 404);

        // Login normal com a senha que ele mesmo criou.
        chamar(POST("/api/auth/login"), null, corpo("email", email, "senha", "Motorista123"), 200);

        // Continua sem poder administrar.
        chamar(POST("/api/caminhoes"), token, corpo("placa", "MOT1A23", "marca", "x", "modelo", "x",
                "ano", 2020, "eixos", 3), 403);
        chamar(GET(MOTORISTAS), token, null, 403);

        JsonNode m = chamar(GET(MOTORISTAS), dono, null, 200).get(0);
        assertEquals("ATIVO", m.get("situacao").asString());
        assertTrue(m.get("conviteExpiraEm").isNull());
    }

    @Test
    void antesDaAtivacaoNaoHaLogin() throws Exception {
        String email = emailNovo();
        convidar(email);
        chamar(POST("/api/auth/login"), null, corpo("email", email, "senha", "QualquerSenha1"), 401);
    }

    @Test
    void conviteEDeUsoUnico() throws Exception {
        String email = emailNovo();
        convidar(email);
        String token = caixa.ultimoToken(email);
        ativar(token, "Motorista123", 200);
        erro(ativar(token, "OutraSenha456", 400), "Convite inválido");
        // A senha continua a primeira.
        chamar(POST("/api/auth/login"), null, corpo("email", email, "senha", "Motorista123"), 200);
    }

    @Test
    void conviteExpiradoNaoAtiva() throws Exception {
        String email = emailNovo();
        long id = convidar(email).get("id").asLong();
        em.flush();
        jdbc.update("update convites set criado_em = now() - interval '3 days', expira_em = now() - interval '1 hour'"
                + " where usuario_id = ?", id);
        em.clear();
        erro(ativar(caixa.ultimoToken(email), "Motorista123", 400), "expirado");
        assertTrue(chamar(GET(MOTORISTAS + "/" + id), dono, null, 200).get("conviteExpiraEm").isNull());
    }

    @Test
    void reenvioInvalidaOConviteAnteriorEPermiteAtivarComONovo() throws Exception {
        String email = emailNovo();
        long id = convidar(email).get("id").asLong();
        String antigo = caixa.ultimoToken(email);

        chamar(POST(MOTORISTAS + "/" + id + "/convite"), dono, null, 200);
        String novo = caixa.ultimoToken(email);
        assertNotEquals(antigo, novo);

        erro(ativar(antigo, "Motorista123", 400), "Convite inválido");
        ativar(novo, "Motorista123", 200);

        // Conta já ativa: não há convite a reenviar.
        erro(chamar(POST(MOTORISTAS + "/" + id + "/convite"), dono, null, 409), "já ativou");
    }

    @Test
    void tokenInventadoNaoAtiva() throws Exception {
        erro(ativar("token-que-nao-existe", "Motorista123", 400), "Convite inválido");
    }

    @Test
    void senhaFracaNaAtivacaoERecusadaEOConviteContinuaValido() throws Exception {
        String email = emailNovo();
        convidar(email);
        String token = caixa.ultimoToken(email);
        erro(ativar(token, "curta1", 400), "pelo menos 8");
        erro(ativar(token, "somenteletras", 400), "uma letra e um número");
        ativar(token, "Motorista123", 200);
    }

    @Test
    void emailEUnicoNoSistemaInteiro() throws Exception {
        Conta outraEmpresa = registrar();
        erro(chamar(POST(MOTORISTAS), dono, corpo("nome", "X", "email", outraEmpresa.email().toUpperCase()), 409),
                "E-mail já cadastrado");
    }

    @Test
    void semEmailConfiguradoResponde503ENadaEGravado() throws Exception {
        caixa.simularIndisponivel(true);
        String email = emailNovo();
        erro(chamar(POST(MOTORISTAS), dono, corpo("nome", "X", "email", email, "caminhaoIds", List.of(caminhaoA)), 503),
                "não está configurado");
        em.flush();
        assertEquals(0, jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email));
        assertEquals(0, chamar(GET(MOTORISTAS), dono, null, 200).size());
    }

    @Test
    void donoAlteraOsCaminhoesDoMotorista() throws Exception {
        String email = emailNovo();
        String token = motoristaAtivo(email, caminhaoA);
        long id = chamar(GET(MOTORISTAS), dono, null, 200).get(0).get("id").asLong();

        JsonNode m = chamar(PUT(MOTORISTAS + "/" + id + "/caminhoes"), dono,
                corpo("caminhaoIds", List.of(caminhaoB, caminhaoB)), 200);
        assertEquals(1, m.get("caminhaoIds").size());
        assertEquals(List.of(caminhaoB), idsListados(token));

        chamar(PUT(MOTORISTAS + "/" + id + "/caminhoes"), dono, corpo("caminhaoIds", List.of()), 200);
        assertTrue(idsListados(token).isEmpty());
    }

    @Test
    void caminhaoExcluidoNaoEVinculadoESomeDoMotorista() throws Exception {
        String token = motoristaAtivo(emailNovo(), caminhaoA, caminhaoB);
        long id = chamar(GET(MOTORISTAS), dono, null, 200).get(0).get("id").asLong();

        chamar(DELETE("/api/caminhoes/" + caminhaoB), dono, null, 204);
        assertEquals(List.of(caminhaoA), idsListados(token));
        chamar(GET("/api/caminhoes/" + caminhaoB), token, null, 404);
        erro(chamar(PUT(MOTORISTAS + "/" + id + "/caminhoes"), dono,
                corpo("caminhaoIds", List.of(caminhaoB)), 409), "excluído");
    }

    @Test
    void desativarOMotoristaInvalidaOTokenNaHora() throws Exception {
        String token = motoristaAtivo(emailNovo(), caminhaoA);
        long id = chamar(GET(MOTORISTAS), dono, null, 200).get(0).get("id").asLong();

        chamar(PUT(MOTORISTAS + "/" + id), dono, corpo("nome", "Desligado", "ativo", false), 200);
        chamar(GET("/api/caminhoes"), token, null, 401);

        JsonNode m = chamar(PUT(MOTORISTAS + "/" + id), dono, corpo("nome", "Religado", "ativo", true), 200);
        assertEquals("ATIVO", m.get("situacao").asString());
        chamar(GET("/api/caminhoes"), token, null, 200);
    }

    @Test
    void donoNaoAtivaContaComConvitePendente() throws Exception {
        long id = convidar(emailNovo()).get("id").asLong();
        erro(chamar(PUT(MOTORISTAS + "/" + id), dono, corpo("nome", "X", "ativo", true), 409), "convite");
    }

    @Test
    void outraEmpresaNaoVeNemAlteraOMotoristaNemUsaSeusCaminhoes() throws Exception {
        long id = convidar(emailNovo(), caminhaoA).get("id").asLong();
        String outro = registrar().token();

        chamar(GET(MOTORISTAS + "/" + id), outro, null, 404);
        chamar(PUT(MOTORISTAS + "/" + id), outro, corpo("nome", "X", "ativo", false), 404);
        chamar(PUT(MOTORISTAS + "/" + id + "/caminhoes"), outro, corpo("caminhaoIds", List.of()), 404);
        chamar(POST(MOTORISTAS + "/" + id + "/convite"), outro, null, 404);
        assertEquals(0, chamar(GET(MOTORISTAS), outro, null, 200).size());

        // O dono da outra empresa não vincula caminhão desta empresa a um motorista dele.
        erro(chamar(POST(MOTORISTAS), outro, corpo("nome", "X", "email", emailNovo(),
                "caminhaoIds", List.of(caminhaoA)), 404), "Caminhão não encontrado");
    }

    @Test
    void donoNaoEGerenciadoComoMotorista() throws Exception {
        // O id do próprio dono não aparece como motorista.
        assertEquals(0, chamar(GET(MOTORISTAS), dono, null, 200).size());
    }

    @Test
    void usuarioTrocaAPropriaSenha() throws Exception {
        String email = emailNovo();
        String token = motoristaAtivo(email);

        erro(chamar(POST("/api/auth/senha"), token, corpo("senhaAtual", "Errada123", "novaSenha", "Nova12345"), 400),
                "Senha atual incorreta");
        erro(chamar(POST("/api/auth/senha"), token, corpo("senhaAtual", "Motorista123", "novaSenha", "fraca"), 400),
                "pelo menos 8");
        chamar(POST("/api/auth/senha"), token, corpo("senhaAtual", "Motorista123", "novaSenha", "Nova12345"), 204);

        chamar(POST("/api/auth/login"), null, corpo("email", email, "senha", "Motorista123"), 401);
        chamar(POST("/api/auth/login"), null, corpo("email", email, "senha", "Nova12345"), 200);
        chamar(POST("/api/auth/senha"), null, corpo("senhaAtual", "Nova12345", "novaSenha", "Outra12345"), 401);
    }
}
