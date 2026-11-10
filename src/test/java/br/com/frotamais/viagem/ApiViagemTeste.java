package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base dos testes de integração de viagens.
 *
 * Usa tokens reais, emitidos por /api/auth/registro, para exercitar o filtro
 * de segurança real (o jwt() do spring-security-test contorna o conversor()).
 * O @Transactional desfaz tudo ao final de cada teste: o MockMvc roda na
 * mesma thread, então as gravações entram na transação do teste.
 */
@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Transactional
public abstract class ApiViagemTeste {

    protected static final String VIAGENS = "/api/viagens";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Empresa nova com um DONO; devolve o token e o e-mail. */
    protected record Conta(String token, String email) {
    }

    protected Conta registrar() throws Exception {
        String id = UUID.randomUUID().toString();
        String email = "viagem-" + id + "@teste.com";
        JsonNode r = chamar(post("/api/auth/registro"), null, Map.of(
                "nomeEmpresa", "Empresa " + id.substring(0, 8),
                "nome", "Dono de teste",
                "email", email,
                "senha", "SenhaTeste123!"), 201);
        return new Conta(r.get("token").asString(), email);
    }

    protected long criarCaminhao(String token) throws Exception {
        String placa = "V" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        return chamar(post("/api/caminhoes"), token, Map.of(
                "placa", placa, "marca", "Marca de teste", "modelo", "Caminhao de teste", "ano", 2022, "eixos", 6,
                "status", "ATIVO", "capacidadeTanqueLitros", 600), 201).get("id").asLong();
    }

    /** Mapa mutável e que aceita nulos, para montar corpos de requisição. */
    protected static Map<String, Object> corpo(Object... chavesEValores) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chavesEValores.length; i += 2) {
            m.put((String) chavesEValores[i], chavesEValores[i + 1]);
        }
        return m;
    }

    protected long criarViagem(String token, Map<String, Object> corpo) throws Exception {
        return chamar(post(VIAGENS), token, corpo, 201).get("id").asLong();
    }

    protected JsonNode buscarViagem(String token, long id) throws Exception {
        return chamar(get(VIAGENS + "/" + id), token, null, 200);
    }

    protected JsonNode diesel(String token, long viagemId, String data, String litros, String valor)
            throws Exception {
        return chamar(post(VIAGENS + "/" + viagemId + "/abastecimentos"), token,
                corpo("tipo", "DIESEL", "data", data, "litros", litros, "valorTotal", valor), 201);
    }

    protected JsonNode arla(String token, long viagemId, String data, String litros, String valor)
            throws Exception {
        return chamar(post(VIAGENS + "/" + viagemId + "/abastecimentos"), token,
                corpo("tipo", "ARLA", "data", data, "litros", litros, "valorTotal", valor), 201);
    }

    protected JsonNode pedagio(String token, long viagemId, String data, String valor) throws Exception {
        return chamar(post(VIAGENS + "/" + viagemId + "/pedagios"), token,
                corpo("data", data, "valor", valor), 201);
    }

    protected JsonNode despesaExtra(String token, long viagemId, String descricao, String data, String valor)
            throws Exception {
        return chamar(post(VIAGENS + "/" + viagemId + "/despesas-extras"), token,
                corpo("descricao", descricao, "data", data, "valor", valor), 201);
    }

    protected JsonNode finalizar(String token, long viagemId, Map<String, Object> corpo, int status)
            throws Exception {
        return chamar(post(VIAGENS + "/" + viagemId + "/finalizar"), token, corpo, status);
    }

    /** Executa a requisição, confere o status e devolve o corpo como JSON (ou null se vazio). */
    protected JsonNode chamar(MockHttpServletRequestBuilder req, String token, Object corpo, int status)
            throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo));
        }
        MvcResult r = mockMvc.perform(req).andReturn();
        String conteudo = r.getResponse().getContentAsString();
        assertEquals(status, r.getResponse().getStatus(),
                () -> r.getRequest().getMethod() + " " + r.getRequest().getRequestURI() + " respondeu " + r.getResponse().getStatus()
                        + ": " + conteudo);
        return conteudo.isBlank() ? null : objectMapper.readTree(conteudo);
    }

    protected static MockHttpServletRequestBuilder GET(String url) {
        return get(url);
    }

    protected static MockHttpServletRequestBuilder POST(String url) {
        return post(url);
    }

    protected static MockHttpServletRequestBuilder PUT(String url) {
        return put(url);
    }

    protected static MockHttpServletRequestBuilder DELETE(String url) {
        return delete(url);
    }

    protected static void dinheiro(String esperado, JsonNode valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor.decimalValue()),
                () -> "esperado " + esperado + ", obtido " + valor);
    }
}
