package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * P15: totais oficiais só com fretes finalizados e completos, margem
 * ponderada pela receita, incompletos apresentados à parte, semana de
 * segunda a domingo, e nada de outra empresa.
 *
 * Março/2025: 03 é segunda-feira e 09 é domingo.
 */
class IndicadoresPonderadosTest extends ApiViagemTeste {

    private static final String INDICADORES = VIAGENS + "/indicadores";

    private String token;
    private long caminhaoA;
    private long incompleto;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoA = criarCaminhao(token);
        long caminhaoB = criarCaminhao(token);
        long caminhaoC = criarCaminhao(token);

        // Domingo da semana anterior (02/03), caminhão C: R$ 1.000, custo 960.
        frete(caminhaoC, "2025-02-28", "2025-03-02", "1000.00", "600.00");
        // Segunda (03/03), caminhão A: R$ 1.000, custo 960 → lucro 40, margem 4%.
        frete(caminhaoA, "2025-03-01", "2025-03-03", "1000.00", "600.00");
        // Domingo (09/03), caminhão A, sem diesel (usa R$ 6,00): R$ 4.000, custo 960 → lucro 3.040, margem 76%.
        frete(caminhaoA, "2025-03-05", "2025-03-09", "4000.00", null);
        // Quinta (06/03), caminhão B, sem diesel e sem histórico: incompleto, R$ 2.500.
        incompleto = frete(caminhaoB, "2025-03-04", "2025-03-06", "2500.00", null);
        // Segunda seguinte (10/03), caminhão A, começando no dia da descarga anterior.
        frete(caminhaoA, "2025-03-09", "2025-03-10", "1000.00", "600.00");

        // Outra empresa, mesma semana: nunca entra.
        String outro = registrar().token();
        long caminhaoOutro = criarCaminhao(outro);
        long v = criarViagem(outro, corpo("caminhaoId", caminhaoOutro, "dataInicio", "2025-03-04",
                "origem", "A", "destino", "B", "valorFrete", "99999.00", "kmInicialCarregado", 0));
        diesel(outro, v, "2025-03-04", "100", "600.00");
        finalizar(outro, v, corpo("dataFim", "2025-03-05", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
    }

    /** 400 km carregado a 2,5 km/L = 160 L; com diesel próprio de 100 L se valorDiesel for informado. */
    private long frete(long caminhao, String inicio, String fim, String valorFrete, String valorDiesel)
            throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhao, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", valorFrete, "kmInicialCarregado", 0));
        if (valorDiesel != null) {
            diesel(token, id, inicio, "100", valorDiesel);
        }
        finalizar(token, id, corpo("dataFim", fim, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200);
        return id;
    }

    @Test
    void semanaDeSegundaADomingoComMargemPonderada() throws Exception {
        for (String dia : List.of("2025-03-03", "2025-03-05", "2025-03-09")) {
            JsonNode r = chamar(GET(INDICADORES + "?semana=" + dia), token, null, 200);
            assertEquals("2025-03-03", r.get("de").asString());
            assertEquals("2025-03-09", r.get("ate").asString());
            assertEquals(3, r.get("viagensFinalizadas").asInt());
            assertEquals(2, r.get("viagensConsideradas").asInt());

            dinheiro("5000.00", r.get("receitaTotal"));
            dinheiro("1920.00", r.get("custoOperacionalTotal"));
            dinheiro("3080.00", r.get("lucroTotal"));
            // Ponderada: 3.080 ÷ 5.000 = 61,60%. A média simples das margens (4% e 76%) daria 40%.
            dinheiro("61.60", r.get("margemPercentual"));

            // Incompleto: fora dos totais, apresentado à parte.
            assertEquals(1, r.get("viagensIncompletas").asInt());
            assertEquals(incompleto, r.get("viagensComCalculoIncompleto").get(0).asLong());
            dinheiro("2500.00", r.get("valorFreteIncompletasNaoIncluido"));
        }
    }

    @Test
    void mesCalendarioEIntervaloInclusivo() throws Exception {
        JsonNode mes = chamar(GET(INDICADORES + "?ano=2025&mes=3"), token, null, 200);
        assertEquals(5, mes.get("viagensFinalizadas").asInt());
        assertEquals(4, mes.get("viagensConsideradas").asInt());
        dinheiro("7000.00", mes.get("receitaTotal"));
        dinheiro("3840.00", mes.get("custoOperacionalTotal"));
        dinheiro("3160.00", mes.get("lucroTotal"));
        dinheiro("45.14", mes.get("margemPercentual"));   // 3.160 ÷ 7.000

        JsonNode fev = chamar(GET(INDICADORES + "?ano=2025&mes=2"), token, null, 200);
        assertEquals(0, fev.get("viagensFinalizadas").asInt());   // pela data final, não a inicial

        // de/até inclusivos nas duas pontas.
        JsonNode intervalo = chamar(GET(INDICADORES + "?de=2025-03-03&ate=2025-03-09"), token, null, 200);
        assertEquals(3, intervalo.get("viagensFinalizadas").asInt());
        dinheiro("61.60", intervalo.get("margemPercentual"));

        JsonNode soA = chamar(GET(INDICADORES + "?semana=2025-03-05&caminhaoId=" + caminhaoA), token, null, 200);
        assertEquals(2, soA.get("viagensFinalizadas").asInt());
        assertEquals(0, soA.get("viagensIncompletas").asInt());
        dinheiro("0.00", soA.get("valorFreteIncompletasNaoIncluido"));
    }

    @Test
    void semanaNaoCombinaComOutrosFiltros() throws Exception {
        chamar(GET(INDICADORES + "?semana=2025-03-05&ano=2025"), token, null, 400);
        chamar(GET(INDICADORES + "?semana=2025-03-05&de=2025-03-01"), token, null, 400);
        chamar(GET(INDICADORES + "?semana=05-03-2025"), token, null, 400);
    }
}
