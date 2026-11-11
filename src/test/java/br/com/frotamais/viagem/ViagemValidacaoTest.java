package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Recusas de dados inválidos e de operações incoerentes com o estado da viagem. */
class ViagemValidacaoTest extends ApiViagemTeste {

    private static final String AMANHA =
            LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();

    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
    }

    private long viagemAberta() throws Exception {
        return criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialVazio", 1000));
    }

    private JsonNode erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho),
                () -> "mensagem sem '" + trecho + "': " + resposta);
        return resposta;
    }

    @Test
    void recusaCamposObrigatoriosEValoresForaDoFormato() throws Exception {
        chamar(POST(VIAGENS), token, corpo("dataInicio", "2025-05-10", "kmInicialVazio", 0), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "kmInicialVazio", 0), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", -1), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0, "valorFrete", "0"), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0, "valorFrete", "10.123"), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0, "consumoMedioVazio", "0"), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0, "consumoMedioVazio", "120"), 400);
        assertEquals(0, chamar(GET(VIAGENS), token, null, 200).size());
    }

    @Test
    void recusaQuilometragemIncoerente() throws Exception {
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10"), 400),
                "quilometragem inicial");
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 1000, "kmInicialCarregado", 1000), 400), "carregamento deve ser maior");
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialCarregado", 1000, "consumoMedioVazio", "3.00"), 400), "sem deslocamento vazio");

        long id = viagemAberta();
        chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", "2025-05-10", "origem", "A",
                "destino", "B", "valorFrete", "1000.00", "kmInicialVazio", 1000, "kmInicialCarregado", 1100), 200);
        erro(finalizar(token, id, corpo("dataFim", "2025-05-11", "kmFinalCarregado", 1100,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 400), "final deve ser maior");
    }

    @Test
    void recusaDatasFuturasOuForaDeOrdem() throws Exception {
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", AMANHA,
                "kmInicialVazio", 0), 400), "futuro");

        long id = viagemAberta();
        chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", "2025-05-10", "origem", "A",
                "destino", "B", "valorFrete", "1000.00", "kmInicialVazio", 1000, "kmInicialCarregado", 1100), 200);
        erro(finalizar(token, id, corpo("dataFim", "2025-05-09", "kmFinalCarregado", 1500,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 400), "anterior à data de início");
        erro(finalizar(token, id, corpo("dataFim", AMANHA, "kmFinalCarregado", 1500,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 400), "futuro");
    }

    @Test
    void finalizacaoExigeDadosCompletos() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 1000));
        JsonNode r = finalizar(token, id, corpo("dataFim", "2025-05-11", "kmFinalCarregado", 1500,
                "consumoMedioCarregado", "2.50"), 400);
        // Sem km de carregamento, a coerência da quilometragem falha primeiro.
        erro(r, "carregamento");

        chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", "2025-05-10",
                "kmInicialVazio", 1000, "kmInicialCarregado", 1100), 200);
        r = finalizar(token, id, corpo("dataFim", "2025-05-11", "kmFinalCarregado", 1500,
                "consumoMedioCarregado", "2.50"), 400);
        erro(r, "valor do frete");
        erro(r, "origem");
        erro(r, "destino");
        erro(r, "consumo médio vazio");
        assertEquals("EM_ANDAMENTO", buscarViagem(token, id).get("status").asString());
    }

    @Test
    void dataFinalEKmFinalSoPelaFinalizacao() throws Exception {
        long id = viagemAberta();
        erro(chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", "2025-05-10",
                "kmInicialVazio", 1000, "kmInicialCarregado", 1100, "kmFinalCarregado", 1500), 400),
                "finalização");
    }

    @Test
    void recusaLancamentosInvalidos() throws Exception {
        long id = viagemAberta();
        String url = VIAGENS + "/" + id + "/abastecimentos";

        chamar(POST(url), token, corpo("tipo", "DIESEL", "data", "2025-05-10", "litros", "0", "valorTotal", "10.00"), 400);
        chamar(POST(url), token, corpo("tipo", "DIESEL", "data", "2025-05-10", "litros", "10", "valorTotal", "-1.00"), 400);
        chamar(POST(url), token, corpo("data", "2025-05-10", "litros", "10", "valorTotal", "10.00"), 400);
        chamar(POST(url), token, corpo("tipo", "GASOLINA", "data", "2025-05-10", "litros", "10", "valorTotal", "10.00"), 400);
        // Abastecimento anterior ao início é aceito (fora de viagem, vai para a seguinte); pedágio, não.
        erro(chamar(POST(VIAGENS + "/" + id + "/pedagios"), token, corpo("data", "2025-05-09",
                "valor", "10.00"), 400), "anterior ao início");
        erro(chamar(POST(url), token, corpo("tipo", "DIESEL", "data", AMANHA, "litros", "10",
                "valorTotal", "60.00"), 400), "futuro");

        chamar(POST(VIAGENS + "/" + id + "/pedagios"), token, corpo("data", "2025-05-10", "valor", "0"), 400);
        chamar(POST(VIAGENS + "/" + id + "/despesas-extras"), token,
                corpo("descricao", "  ", "data", "2025-05-10", "valor", "10.00"), 400);

        JsonNode v = buscarViagem(token, id);
        assertEquals(0, v.get("abastecimentosDiesel").size());
        assertEquals(0, v.get("pedagios").size());
        assertEquals(0, v.get("despesasExtras").size());
    }

    @Test
    void viagemFinalizadaNaoAceitaLancamentoForaDoPeriodoNemExclusao() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        long pedagio = pedagio(token, id, "2025-05-10", "10.00").get("id").asLong();
        long dieselId = diesel(token, id, "2025-05-11", "50", "300.00").get("id").asLong();

        // Finalizar antes da data de um lançamento é recusado.
        erro(finalizar(token, id, corpo("dataFim", "2025-05-10", "kmFinalCarregado", 300,
                "consumoMedioCarregado", "2.50"), 400), "fora do período");
        finalizar(token, id, corpo("dataFim", "2025-05-12", "kmFinalCarregado", 300,
                "consumoMedioCarregado", "2.50"), 200);

        // Lançamento novo em viagem finalizada: 409. Correção para fora do período: 400.
        erro(chamar(POST(VIAGENS + "/" + id + "/pedagios"), token,
                corpo("data", "2025-05-12", "valor", "10.00"), 409), "não aceita novos lançamentos");
        erro(chamar(PUT(VIAGENS + "/" + id + "/pedagios/" + pedagio), token,
                corpo("data", "2025-05-13", "valor", "10.00"), 400), "posterior ao fim");
        erro(chamar(DELETE(VIAGENS + "/" + id + "/pedagios/" + pedagio), token, null, 409), "não excluídos");
        chamar(DELETE(VIAGENS + "/" + id + "/abastecimentos/" + dieselId), token, null, 409);

        // Correção continua possível.
        chamar(PUT(VIAGENS + "/" + id + "/pedagios/" + pedagio), token,
                corpo("data", "2025-05-10", "valor", "11.00"), 200);
        dinheiro("11.00", buscarViagem(token, id).get("rentabilidade").get("pedagios"));
    }

    @Test
    void lancamentoNaoPodeSerAcessadoPorOutraViagemNemPorOutroTipo() throws Exception {
        long id = viagemAberta();
        long pedagio = pedagio(token, id, "2025-05-10", "10.00").get("id").asLong();
        chamar(PUT(VIAGENS + "/" + id + "/despesas-extras/" + pedagio), token,
                corpo("descricao", "x", "data", "2025-05-10", "valor", "1.00"), 404);
        chamar(DELETE(VIAGENS + "/" + id + "/despesas-extras/" + pedagio), token, null, 404);

        long outroCaminhao = criarCaminhao(token);
        long outra = criarViagem(token, corpo("caminhaoId", outroCaminhao, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0));
        chamar(PUT(VIAGENS + "/" + outra + "/pedagios/" + pedagio), token,
                corpo("data", "2025-05-10", "valor", "1.00"), 404);
        chamar(DELETE(VIAGENS + "/" + outra + "/pedagios/" + pedagio), token, null, 404);
    }

    @Test
    void recusaCaminhaoInexistenteOuInativo() throws Exception {
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", Long.MAX_VALUE, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0), 404), "Caminhão não encontrado");

        chamar(PUT("/api/caminhoes/" + caminhaoId), token, corpo("placa", "INA" + (caminhaoId % 10000),
                "marca", "x", "modelo", "x", "ano", 2020, "eixos", 3, "status", "INATIVO"), 200);
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-10",
                "kmInicialVazio", 0), 409), "inativo");
    }

    @Test
    void viagemInexistenteResponde404() throws Exception {
        chamar(GET(VIAGENS + "/" + Long.MAX_VALUE), token, null, 404);
        finalizar(token, Long.MAX_VALUE, corpo("dataFim", "2025-05-10", "kmFinalCarregado", 1), 404);
        chamar(POST(VIAGENS + "/" + Long.MAX_VALUE + "/pedagios"), token,
                corpo("data", "2025-05-10", "valor", "1.00"), 404);
    }
}
