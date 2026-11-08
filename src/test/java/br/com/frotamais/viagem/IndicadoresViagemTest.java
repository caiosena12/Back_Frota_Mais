package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Indicadores por período: soma só fretes finalizados e com cálculo completo. */
class IndicadoresViagemTest extends ApiViagemTeste {

    private static final String INDICADORES = VIAGENS + "/indicadores";

    private String token;
    private long caminhaoA;
    private long caminhaoB;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoA = criarCaminhao(token);
        caminhaoB = criarCaminhao(token);

        // Março/2025, caminhão A: frete de R$ 2.000, 400 km carregado a 2,5 km/L e R$ 6,00/L (custo 960).
        long m1 = criarViagem(token, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, m1, "2025-03-03", "100", "600.00");
        pedagio(token, m1, "2025-03-03", "40.00");
        finalizar(token, m1, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);

        // Março/2025, caminhão A: 100 km vazio + 500 carregado, sem diesel (usa 6,00 do frete anterior).
        long m2 = criarViagem(token, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-03-10",
                "origem", "B", "destino", "C", "valorFrete", "3000.00",
                "kmInicialVazio", 400, "kmInicialCarregado", 500));
        arla(token, m2, "2025-03-11", "20", "60.00");
        finalizar(token, m2, corpo("dataFim", "2025-03-12", "kmFinalCarregado", 1000,
                "consumoMedioVazio", "4.00", "consumoMedioCarregado", "2.50"), 200);
        // Custo: vazio 25 L × 6 = 150; carregado 200 L × 6 = 1200; Arla 60. Total 1410.

        // Março/2025, caminhão B: sem diesel e sem histórico → cálculo incompleto, fora dos totais.
        long semPreco = criarViagem(token, corpo("caminhaoId", caminhaoB, "dataInicio", "2025-03-20",
                "origem", "X", "destino", "Y", "valorFrete", "9999.00", "kmInicialCarregado", 0));
        finalizar(token, semPreco, corpo("dataFim", "2025-03-21", "kmFinalCarregado", 100,
                "consumoMedioCarregado", "2.50"), 200);

        // Abril/2025, caminhão B: frete com diesel próprio.
        long abril = criarViagem(token, corpo("caminhaoId", caminhaoB, "dataInicio", "2025-03-30",
                "origem", "Y", "destino", "Z", "valorFrete", "1500.00", "kmInicialCarregado", 100));
        diesel(token, abril, "2025-03-30", "50", "320.00");
        finalizar(token, abril, corpo("dataFim", "2025-04-02", "kmFinalCarregado", 300,
                "consumoMedioCarregado", "2.00"), 200);
        // 100 L × 6,40 = 640.

        // Em andamento: nunca entra nos indicadores.
        criarViagem(token, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-03-25",
                "origem", "C", "destino", "D", "valorFrete", "5000.00", "kmInicialCarregado", 1000));
    }

    @Test
    void somaFretesDoMesPelaDataFinal() throws Exception {
        JsonNode r = chamar(GET(INDICADORES + "?ano=2025&mes=3"), token, null, 200);

        assertEquals("2025-03-01", r.get("de").asString());
        assertEquals("2025-03-31", r.get("ate").asString());
        assertEquals("DATA_FIM", r.get("criterioPeriodo").asString());
        assertEquals(3, r.get("viagensFinalizadas").asInt());
        assertEquals(2, r.get("viagensConsideradas").asInt());
        assertEquals(1, r.get("viagensComCalculoIncompleto").size());
        dinheiro("5000.00", r.get("receitaTotal"));
        dinheiro("150.00", r.get("custoDieselVazio"));
        dinheiro("2160.00", r.get("custoDieselCarregado"));
        dinheiro("60.00", r.get("custoArla"));
        dinheiro("40.00", r.get("pedagios"));
        dinheiro("0.00", r.get("despesasExtras"));
        dinheiro("2410.00", r.get("custoOperacionalTotal"));
        dinheiro("2590.00", r.get("lucroTotal"));
        dinheiro("51.80", r.get("margemPercentual"));
        assertEquals(100, r.get("kmVazio").asLong());
        assertEquals(900, r.get("kmCarregado").asLong());
    }

    @Test
    void filtraPorAnoIntervaloECaminhao() throws Exception {
        JsonNode ano = chamar(GET(INDICADORES + "?ano=2025"), token, null, 200);
        assertEquals(4, ano.get("viagensFinalizadas").asInt());
        dinheiro("6500.00", ano.get("receitaTotal"));
        dinheiro("3050.00", ano.get("custoOperacionalTotal"));

        // Uma "semana" é um intervalo de/até definido pelo cliente.
        JsonNode semana = chamar(GET(INDICADORES + "?de=2025-03-31&ate=2025-04-06"), token, null, 200);
        assertEquals(1, semana.get("viagensFinalizadas").asInt());
        dinheiro("1500.00", semana.get("receitaTotal"));
        dinheiro("860.00", semana.get("lucroTotal"));

        JsonNode soA = chamar(GET(INDICADORES + "?ano=2025&caminhaoId=" + caminhaoA), token, null, 200);
        assertEquals(2, soA.get("viagensFinalizadas").asInt());
        dinheiro("5000.00", soA.get("receitaTotal"));

        JsonNode tudo = chamar(GET(INDICADORES), token, null, 200);
        assertEquals(4, tudo.get("viagensFinalizadas").asInt());
        assertTrue(tudo.get("de").isNull());

        JsonNode vazio = chamar(GET(INDICADORES + "?ano=2024"), token, null, 200);
        assertEquals(0, vazio.get("viagensFinalizadas").asInt());
        dinheiro("0.00", vazio.get("receitaTotal"));
        assertTrue(vazio.get("margemPercentual").isNull());
    }

    @Test
    void recusaFiltrosInvalidos() throws Exception {
        chamar(GET(INDICADORES + "?mes=3"), token, null, 400);
        chamar(GET(INDICADORES + "?ano=2025&mes=13"), token, null, 400);
        chamar(GET(INDICADORES + "?ano=2025&de=2025-01-01"), token, null, 400);
        chamar(GET(INDICADORES + "?de=2025-04-01&ate=2025-03-01"), token, null, 400);
        chamar(GET(INDICADORES + "?de=01-03-2025"), token, null, 400);
        chamar(GET(INDICADORES + "?caminhaoId=" + Long.MAX_VALUE), token, null, 404);
    }

    @Test
    void listagemMostraResumoFinanceiroDeCadaFrete() throws Exception {
        JsonNode lista = chamar(GET(VIAGENS + "?status=FINALIZADA&caminhaoId=" + caminhaoA), token, null, 200);
        assertEquals(2, lista.size());
        // Mais recente primeiro.
        assertEquals("2025-03-10", lista.get(0).get("dataInicio").asString());
        dinheiro("1590.00", lista.get(0).get("lucro"));
        assertEquals(100, lista.get(0).get("kmVazio").asInt());
        assertTrue(lista.get(0).get("calculoCompleto").asBoolean());
    }
}
