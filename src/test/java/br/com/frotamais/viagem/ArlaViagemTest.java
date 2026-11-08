package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * P18, Arla: só litros e valor positivos. Sem limite de preço ou de consumo
 * (nenhum foi definido) e sem a capacidade do tanque de diesel, que não é a
 * do tanque de Arla (o caminhão não tem esse campo).
 */
class ArlaViagemTest extends ApiViagemTeste {

    @Test
    void arlaExigeLitrosEValorPositivosESemOutrosLimites() throws Exception {
        String token = registrar().token();
        long viagem = criarViagem(token, corpo("caminhaoId", criarCaminhao(token), "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        String url = VIAGENS + "/" + viagem + "/abastecimentos";

        for (String[] lv : new String[][] {{"0", "10.00"}, {"-1", "10.00"}, {"10", "0"}, {"10", "-5.00"},
                {null, "10.00"}, {"10", null}}) {
            chamar(POST(url), token, corpo("tipo", "ARLA", "data", "2025-03-01", "litros", lv[0],
                    "valorTotal", lv[1]), 400);
        }

        // R$ 0,01/L e R$ 100,00/L: fora da faixa do diesel, mas Arla não tem faixa de preço.
        arla(token, viagem, "2025-03-01", "1000", "10.00");
        arla(token, viagem, "2025-03-01", "1", "100.00");
        // Acima da capacidade do tanque de diesel (600 L): não se aplica ao Arla.
        arla(token, viagem, "2025-03-01", "700", "2100.00");

        JsonNode v = buscarViagem(token, viagem);
        assertEquals(3, v.get("abastecimentosArla").size());
        dinheiro("2210.00", v.get("rentabilidade").get("custoArla"));
    }
}
