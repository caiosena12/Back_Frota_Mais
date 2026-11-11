package br.com.frotamais.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;

import br.com.frotamais.viagem.ApiViagemTeste;
import tools.jackson.databind.JsonNode;

/**
 * Contrato de erro da API: todo erro sai como {"status", "erro", "detalhes"},
 * inclusive os 401/403 decididos pelos filtros de segurança e os corpos JSON
 * ilegíveis, que antes caíam no /error do Spring com outro formato.
 */
class ErroPadronizadoTest extends ApiViagemTeste {

    private static void formatoPadrao(JsonNode erro, int status, String categoria) {
        assertEquals(status, erro.get("status").asInt(), erro::toString);
        assertEquals(categoria, erro.get("erro").asString(), erro::toString);
        assertTrue(erro.get("detalhes").isArray() && !erro.get("detalhes").isEmpty(), erro::toString);
    }

    private static void contem(JsonNode erro, String trecho) {
        assertTrue(erro.get("detalhes").toString().contains(trecho), erro::toString);
    }

    @Test
    void semTokenResponde401EmJsonComWwwAuthenticate() throws Exception {
        MvcResult r = mockMvc.perform(GET("/api/caminhoes")).andReturn();
        assertEquals(401, r.getResponse().getStatus());
        assertNotNull(r.getResponse().getHeader("WWW-Authenticate"));
        formatoPadrao(objectMapper.readTree(r.getResponse().getContentAsString()), 401, "Não autenticado");
    }

    @Test
    void tokenAdulteradoResponde401EmJson() throws Exception {
        String token = registrar().token();
        String adulterado = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        formatoPadrao(chamar(GET("/api/caminhoes"), adulterado, null, 401), 401, "Não autenticado");
    }

    @Test
    void perfilSemPermissaoResponde403EmJson() throws Exception {
        MvcResult r = mockMvc.perform(GET("/api/viagens/indicadores")
                .with(jwt().jwt(j -> j.claim("empresaId", 1L))
                        .authorities(new SimpleGrantedAuthority("ROLE_MOTORISTA")))).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        formatoPadrao(objectMapper.readTree(r.getResponse().getContentAsString()), 403, "Acesso negado");
    }

    @Test
    void naoEncontradoResponde404ComMensagem() throws Exception {
        JsonNode erro = chamar(GET("/api/caminhoes/999999999"), registrar().token(), null, 404);
        formatoPadrao(erro, 404, "Não encontrado");
        contem(erro, "Caminhão não encontrado");
    }

    @Test
    void placaDuplicadaResponde409ComMensagem() throws Exception {
        String token = registrar().token();
        var corpo = corpo("placa", "ERR1A23", "marca", "Volvo", "modelo", "FH", "ano", 2020, "eixos", 6);
        chamar(POST("/api/caminhoes"), token, corpo, 201);
        JsonNode erro = chamar(POST("/api/caminhoes"), token, corpo, 409);
        formatoPadrao(erro, 409, "Conflito de dados");
        contem(erro, "placa");
    }

    @Test
    void validacaoDeCampoResponde400ComOCampo() throws Exception {
        JsonNode erro = chamar(POST("/api/caminhoes"), registrar().token(), corpo("placa", "", "ano", 2020), 400);
        formatoPadrao(erro, 400, "Dados inválidos");
        contem(erro, "placa");
    }

    @Test
    void jsonMalformadoResponde400() throws Exception {
        String token = registrar().token();
        MvcResult r = mockMvc.perform(POST("/api/caminhoes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"placa\": ")).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        JsonNode erro = objectMapper.readTree(r.getResponse().getContentAsString());
        formatoPadrao(erro, 400, "Dados inválidos");
        contem(erro, "JSON malformado");
    }

    @Test
    void enumInvalidoNoCorpoResponde400ComValoresAceitos() throws Exception {
        String token = registrar().token();
        long caminhao = criarCaminhao(token);
        long viagem = criarViagem(token, corpo("caminhaoId", caminhao, "dataInicio", "2025-01-10",
                "kmInicialCarregado", 1000));
        JsonNode erro = chamar(POST(VIAGENS + "/" + viagem + "/abastecimentos"), token,
                corpo("tipo", "GASOLINA", "data", "2025-01-10", "litros", "10", "valorTotal", "60"), 400);
        formatoPadrao(erro, 400, "Dados inválidos");
        contem(erro, "tipo");
        contem(erro, "DIESEL");
    }

    @Test
    void dataInvalidaNoCorpoResponde400ComFormato() throws Exception {
        String token = registrar().token();
        JsonNode erro = chamar(POST(VIAGENS), token,
                corpo("caminhaoId", criarCaminhao(token), "dataInicio", "10/01/2025", "kmInicialCarregado", 1), 400);
        formatoPadrao(erro, 400, "Dados inválidos");
        contem(erro, "dataInicio");
        contem(erro, "AAAA-MM-DD");
    }

    @Test
    void parametroDeConsultaInvalidoResponde400() throws Exception {
        JsonNode erro = chamar(GET(VIAGENS + "?status=QUALQUER"), registrar().token(), null, 400);
        formatoPadrao(erro, 400, "Dados inválidos");
        contem(erro, "status");
    }

    @Test
    void metodoNaoSuportadoResponde405() throws Exception {
        JsonNode erro = chamar(PUT("/api/caminhoes"), registrar().token(), corpo(), 405);
        formatoPadrao(erro, 405, "Método não permitido");
    }

    @Test
    void loginInvalidoResponde401ComMensagem() throws Exception {
        JsonNode erro = chamar(POST("/api/auth/login"), null,
                corpo("email", "ninguem-" + System.nanoTime() + "@teste.com", "senha", "SenhaErrada1"), 401);
        formatoPadrao(erro, 401, "Não autenticado");
        contem(erro, "E-mail ou senha inválidos");
    }
}
