package br.com.frotamais.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.frotamais.caminhao.CaminhaoRequest;
import br.com.frotamais.motorista.VinculosRequest;
import br.com.frotamais.viagem.AbastecimentoRequest;
import br.com.frotamais.viagem.ApiViagemTeste;
import br.com.frotamais.viagem.FinalizacaoRequest;
import br.com.frotamais.viagem.ViagemCriacaoRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Leitura estrita do JSON (comum/ConfigJackson), em dois níveis:
 * <ol>
 * <li>desserialização com o ObjectMapper real da aplicação, para provar que o
 * Jackson não trunca 1.5 nem converte "152430" antes da validação;</li>
 * <li>endpoints reais, com token real: resposta 400 no formato padrão e nada
 * gravado.</li>
 * </ol>
 */
class LeituraEstritaJsonTest extends ApiViagemTeste {

    private String dono;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
    }

    private static void detalhe(JsonNode erro, String trecho) {
        assertEquals(400, erro.get("status").asInt(), erro::toString);
        assertTrue(erro.get("detalhes").toString().contains(trecho), erro::toString);
    }

    private Map<String, Object> caminhao(Object ano, Object eixos, Object tanque) {
        String placa = "J" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return corpo("placa", placa, "marca", "Volvo", "modelo", "FH", "ano", ano, "eixos", eixos,
                "status", null, "capacidadeTanqueLitros", tanque);
    }

    private int quantidade(String rota) throws Exception {
        return chamar(GET(rota), dono, null, 200).size();
    }

    // ---------- 1. Desserialização (ObjectMapper da aplicação) ----------

    @Test
    void jacksonAceitaInteiroDoJson() throws Exception {
        CaminhaoRequest c = objectMapper.readValue(
                "{\"placa\":\"ABC1D23\",\"marca\":\"V\",\"modelo\":\"F\",\"ano\":2022,\"eixos\":6,\"capacidadeTanqueLitros\":600}",
                CaminhaoRequest.class);
        assertEquals(2022, c.ano());
        assertEquals(6, c.eixos());
        AbastecimentoRequest a = objectMapper.readValue(
                "{\"tipo\":\"DIESEL\",\"data\":\"2025-06-02\",\"quilometragem\":0,\"litros\":\"100.5\",\"valorTotal\":600}",
                AbastecimentoRequest.class);
        assertEquals(0, a.quilometragem());
        // Valores com casas decimais continuam aceitando número ou texto numérico.
        assertEquals(new BigDecimal("100.5"), a.litros());
        assertEquals(new BigDecimal("600"), a.valorTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.5", "152430.7", "\"152430\"", "\"\"", "true", "1e3" })
    void jacksonRecusaDecimalTextoEBooleanoEmCampoInteiro(String valor) {
        String km = "{\"tipo\":\"DIESEL\",\"data\":\"2025-06-02\",\"quilometragem\":" + valor
                + ",\"litros\":\"1\",\"valorTotal\":\"1\"}";
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(km, AbastecimentoRequest.class), valor);
        String ano = "{\"placa\":\"A\",\"marca\":\"V\",\"modelo\":\"F\",\"ano\":" + valor + ",\"eixos\":6}";
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(ano, CaminhaoRequest.class), valor);
        String finalizar = "{\"dataFim\":\"2025-06-03\",\"kmFinalCarregado\":" + valor + "}";
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(finalizar, FinalizacaoRequest.class), valor);
    }

    @Test
    void jacksonRecusaDecimalEmIdLongEEmListaDeIds() {
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(
                "{\"caminhaoId\":1.5,\"dataInicio\":\"2025-06-01\"}", ViagemCriacaoRequest.class));
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(
                "{\"caminhaoIds\":[1, 2.5]}", VinculosRequest.class));
        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(
                "{\"caminhaoIds\":[\"1\"]}", VinculosRequest.class));
    }

    @Test
    void jacksonRecusaCampoDesconhecido() {
        UnrecognizedPropertyException e = assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue(
                "{\"tipo\":\"DIESEL\",\"data\":\"2025-06-02\",\"hora\":\"14:30\",\"quilometragem\":1,\"litros\":1,\"valorTotal\":1}",
                AbastecimentoRequest.class));
        assertEquals("hora", e.getPropertyName());
    }

    // ---------- 2. Endpoints: 400 e nada gravado ----------

    @Test
    void caminhaoAceitaLimitesERecusaDecimalETextoSemGravar() throws Exception {
        int antes = quantidade("/api/caminhoes");
        chamar(POST("/api/caminhoes"), dono, caminhao(1950, 1, 1), 201);
        chamar(POST("/api/caminhoes"), dono, caminhao(2100, 20, null), 201);
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020.5, 6, 600), 400), "Campo 'ano' com valor inválido.");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020, "6", 600), 400), "Campo 'eixos' com valor inválido.");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020, 6, 600.7), 400),
                "Campo 'capacidadeTanqueLitros' com valor inválido.");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(1949, 6, 600), 400), "ano");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020, 21, 600), 400), "eixos");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020, 0, 600), 400), "eixos");
        detalhe(chamar(POST("/api/caminhoes"), dono, caminhao(2020, 6, -1), 400), "capacidadeTanqueLitros");
        Map<String, Object> comCor = caminhao(2020, 6, 600);
        comCor.put("cor", "branco");
        detalhe(chamar(POST("/api/caminhoes"), dono, comCor, 400), "Campo desconhecido: 'cor'.");
        assertEquals(antes + 2, quantidade("/api/caminhoes"));
    }

    @Test
    void edicaoDeCaminhaoComDecimalNaoAlteraNada() throws Exception {
        Map<String, Object> c = caminhao(2022, 6, 600);
        c.put("ano", 2022.9);
        chamar(PUT("/api/caminhoes/" + caminhaoId), dono, c, 400);
        assertEquals(2022, chamar(GET("/api/caminhoes/" + caminhaoId), dono, null, 200).get("ano").asInt());
    }

    @Test
    void viagemRecusaKmEIdDecimaisECamposDesconhecidosSemCriar() throws Exception {
        Map<String, Object> v = corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01", "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 150000.7);
        detalhe(chamar(POST(VIAGENS), dono, v, 400), "Campo 'kmInicialCarregado' com valor inválido.");
        v.put("kmInicialCarregado", 150000);
        v.put("caminhaoId", caminhaoId + 0.5);
        detalhe(chamar(POST(VIAGENS), dono, v, 400), "Campo 'caminhaoId' com valor inválido.");
        v.put("caminhaoId", caminhaoId);
        v.put("kmInicialVazio", "149900");
        detalhe(chamar(POST(VIAGENS), dono, v, 400), "Campo 'kmInicialVazio' com valor inválido.");
        v.put("kmInicialVazio", null);
        v.put("km", 150000);
        detalhe(chamar(POST(VIAGENS), dono, v, 400), "Campo desconhecido: 'km'.");
        v.remove("km");
        // Campo desconhecido dentro da lista de abastecimentos da abertura.
        v.put("abastecimentos", List.of(corpo("tipo", "DIESEL", "data", "2025-05-30", "quilometragem", 149990,
                "hora", "10:00", "litros", "100", "valorTotal", "600.00")));
        detalhe(chamar(POST(VIAGENS), dono, v, 400), "Campo desconhecido: 'hora'.");
        assertEquals(0, quantidade(VIAGENS));

        v.remove("abastecimentos");
        long id = criarViagem(dono, v);
        Map<String, Object> fim = corpo("dataFim", "2025-06-03", "kmFinalCarregado", 150500.5);
        detalhe(finalizar(dono, id, fim, 400), "Campo 'kmFinalCarregado' com valor inválido.");
        assertEquals("EM_ANDAMENTO", buscarViagem(dono, id).get("status").asString());
    }

    @Test
    void motoristaRecusaIdDecimalNaListaDeCaminhoesSemConvidar() throws Exception {
        String email = "carlos-" + UUID.randomUUID() + "@teste.com";
        Map<String, Object> m = corpo("nome", "Carlos", "email", email,
                "caminhaoIds", List.of(caminhaoId + 0.5));
        chamar(POST("/api/motoristas"), dono, m, 400);
        m.put("caminhaoIds", List.of(String.valueOf(caminhaoId)));
        chamar(POST("/api/motoristas"), dono, m, 400);
        assertEquals(0, quantidade("/api/motoristas"));
        assertEquals(0, caixaDeSaida.paraEndereco(email).size(), "nenhum convite enviado");
    }

    @Test
    void loginERegistroRecusamCampoDesconhecido() throws Exception {
        detalhe(chamar(POST("/api/auth/login"), null, corpo("email", "a@teste.com", "senha", "x", "lembrar", true), 400),
                "Campo desconhecido: 'lembrar'.");
        detalhe(chamar(POST("/api/auth/registro"), null, corpo("nomeEmpresa", "E", "nome", "N",
                "email", "novo-" + UUID.randomUUID() + "@teste.com", "senha", "SenhaTeste123!", "perfil", "DONO"), 400),
                "Campo desconhecido: 'perfil'.");
    }

    @Test
    void pedagioEDespesaRecusamCampoDesconhecidoSemGravar() throws Exception {
        long id = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01", "origem", "A",
                "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 150000));
        String url = VIAGENS + "/" + id;
        detalhe(chamar(POST(url + "/pedagios"), dono, corpo("data", "2025-06-01", "valor", "10.00", "valorPago", "9.00"), 400),
                "Campo desconhecido: 'valorPago'.");
        detalhe(chamar(POST(url + "/despesas-extras"), dono,
                corpo("descricao", "Refeição", "data", "2025-06-01", "valor", "30.00", "categoria", "ALIMENTACAO"), 400),
                "Campo desconhecido: 'categoria'.");
        JsonNode v = buscarViagem(dono, id);
        assertEquals(0, v.get("pedagios").size());
        assertEquals(0, v.get("despesasExtras").size());
    }
}
