package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Indicadores por km, consumo, diesel pago × estimado e manutenções, da
 * empresa e por caminhão.
 *
 * Frete do caminhão A em junho: 100 km vazio a 2,50 km/l (40 l) e 400 km
 * carregado a 2,00 km/l (200 l); diesel pago 100 l por R$ 600 (R$ 6,00/l).
 * Custo: 240 + 1.200 de diesel estimado + 60 de pedágio = 1.500. Frete 3.000,
 * lucro 1.500. Manutenções em junho: A R$ 400, B R$ 100 (uma de B excluída e
 * uma de A em julho não contam).
 */
class IndicadoresComplementaresTest extends ApiViagemTeste {

    private static final String JUNHO = VIAGENS + "/indicadores?ano=2025&mes=6";

    private String dono;
    private long caminhaoA;
    private long caminhaoB;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoA = criarCaminhao(dono);
        caminhaoB = criarCaminhao(dono);

        long v = criarViagem(dono, corpo("caminhaoId", caminhaoA, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "3000.00",
                "kmInicialVazio", 0, "kmInicialCarregado", 100));
        diesel(dono, v, "2025-06-01", "100", "600.00");
        pedagio(dono, v, "2025-06-02", "60.00");
        finalizar(dono, v, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 500,
                "consumoMedioVazio", "2.50", "consumoMedioCarregado", "2.00"), 200);

        manutencao(caminhaoA, "2025-06-10", "400.00");
        manutencao(caminhaoA, "2025-07-01", "999.00");
        manutencao(caminhaoB, "2025-06-15", "100.00");
        long excluida = manutencao(caminhaoB, "2025-06-16", "50.00");
        chamar(DELETE("/api/manutencoes/" + excluida), dono, null, 204);
    }

    private long manutencao(long caminhao, String data, String valor) throws Exception {
        return chamar(POST("/api/manutencoes"), dono, corpo("caminhaoId", caminhao, "data", data,
                "descricao", "Revisão", "valor", valor), 201).get("id").asLong();
    }

    @Test
    void indicadoresDaEmpresaNoMes() throws Exception {
        JsonNode i = chamar(GET(JUNHO), dono, null, 200);
        dinheiro("1500.00", i.get("lucroTotal"));
        dinheiro("3.00", i.get("custoPorKm"));        // 1.500 / 500 km
        dinheiro("3.00", i.get("lucroPorKm"));
        dinheiro("20.00", i.get("percentualKmVazio")); // 100 de 500 km
        dinheiro("2.08", i.get("consumoMedioKmPorLitro")); // 500 km / 240 l
        dinheiro("600.00", i.get("dieselPago"));
        dinheiro("1440.00", i.get("dieselEstimado"));
        dinheiro("-840.00", i.get("diferencaDieselPagoEstimado"));
        // O pago não entra no custo: o custo continua 1.500.
        dinheiro("1500.00", i.get("custoOperacionalTotal"));

        assertEquals(2, i.get("quantidadeManutencoes").asLong());
        dinheiro("500.00", i.get("custoManutencoes"));
        dinheiro("1000.00", i.get("resultadoAposManutencoes"));
    }

    @Test
    void filtroPorCaminhao() throws Exception {
        JsonNode a = chamar(GET(JUNHO + "&caminhaoId=" + caminhaoA), dono, null, 200);
        assertEquals(1, a.get("quantidadeManutencoes").asLong());
        dinheiro("1100.00", a.get("resultadoAposManutencoes"));

        JsonNode b = chamar(GET(JUNHO + "&caminhaoId=" + caminhaoB), dono, null, 200);
        assertEquals(0, b.get("viagensFinalizadas").asInt());
        assertTrue(b.get("custoPorKm").isNull(), b::toString);
        assertTrue(b.get("consumoMedioKmPorLitro").isNull(), b::toString);
        dinheiro("100.00", b.get("custoManutencoes"));
        dinheiro("-100.00", b.get("resultadoAposManutencoes"));
    }

    @Test
    void indicadoresPorCaminhao() throws Exception {
        JsonNode lista = chamar(GET(VIAGENS + "/indicadores/por-caminhao?ano=2025&mes=6"), dono, null, 200);
        assertEquals(2, lista.size());
        for (JsonNode item : lista) {
            JsonNode i = item.get("indicadores");
            assertEquals(item.get("caminhaoId").asLong(), i.get("caminhaoId").asLong());
            if (item.get("caminhaoId").asLong() == caminhaoA) {
                assertEquals(1, i.get("viagensFinalizadas").asInt());
                dinheiro("1100.00", i.get("resultadoAposManutencoes"));
            } else {
                assertEquals(caminhaoB, item.get("caminhaoId").asLong());
                dinheiro("-100.00", i.get("resultadoAposManutencoes"));
            }
        }
        // Julho: só a manutenção de A.
        JsonNode julho = chamar(GET(VIAGENS + "/indicadores/por-caminhao?ano=2025&mes=7"), dono, null, 200);
        assertEquals(1, julho.size());
        assertEquals(caminhaoA, julho.get(0).get("caminhaoId").asLong());
        dinheiro("999.00", julho.get(0).get("indicadores").get("custoManutencoes"));
    }

    @Test
    void periodoVazioNaoInventaValores() throws Exception {
        JsonNode i = chamar(GET(VIAGENS + "/indicadores?ano=2024&mes=1"), dono, null, 200);
        assertTrue(i.get("custoPorKm").isNull(), i::toString);
        assertTrue(i.get("percentualKmVazio").isNull(), i::toString);
        dinheiro("0.00", i.get("custoManutencoes"));
        assertEquals(0, chamar(GET(VIAGENS + "/indicadores/por-caminhao?ano=2024&mes=1"), dono, null, 200).size());
    }

    @Test
    void outraEmpresaEMotoristaNaoVeem() throws Exception {
        String outro = registrar().token();
        assertEquals(0, chamar(GET(VIAGENS + "/indicadores/por-caminhao?ano=2025&mes=6"), outro, null, 200).size());
        JsonNode i = chamar(GET(JUNHO), outro, null, 200);
        assertEquals(0, i.get("quantidadeManutencoes").asLong());
        chamar(GET(JUNHO + "&caminhaoId=" + caminhaoA), outro, null, 404);

        String motorista = criarMotorista(dono, caminhaoA).token();
        chamar(GET(VIAGENS + "/indicadores/por-caminhao"), motorista, null, 403);
        chamar(GET(JUNHO), motorista, null, 403);
    }
}
