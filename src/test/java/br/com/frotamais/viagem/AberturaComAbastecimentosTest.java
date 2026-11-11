package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.Datas;
import tools.jackson.databind.JsonNode;

/**
 * Abertura da viagem com os abastecimentos feitos antes dela (fora de
 * viagem), numa só transação: tudo ou nada. Datas reais preservadas; o custo
 * vai para este frete, nunca para o anterior.
 */
class AberturaComAbastecimentosTest extends ApiViagemTeste {

    private String dono;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
    }

    private static Map<String, Object> diesel(String data, String litros, String valor) {
        return corpo("tipo", "DIESEL", "data", data, "litros", litros, "valorTotal", valor);
    }

    private Map<String, Object> abertura(String inicio, List<?> abastecimentos) {
        return corpo("caminhaoId", caminhaoId, "dataInicio", inicio, "origem", "A", "destino", "B",
                "valorFrete", "3000.00", "kmInicialCarregado", 1000, "abastecimentos", abastecimentos);
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void primeiroFreteAbreComAbastecimentosAnterioresSemLimiteDeDias() throws Exception {
        JsonNode v = chamar(POST(VIAGENS), dono, abertura("2025-06-10", List.of(
                diesel("2025-01-05", "200", "1200.00"),
                corpo("tipo", "ARLA", "data", "2025-06-09", "hora", "18:40", "litros", "20", "valorTotal", "70.00"),
                diesel("2025-06-10", "100", "700.00"))), 201);
        JsonNode dieselLista = v.get("abastecimentosDiesel");
        assertEquals(2, dieselLista.size());
        assertEquals("2025-01-05", dieselLista.get(0).get("data").asString());
        assertEquals("18:40:00", v.get("abastecimentosArla").get(0).get("hora").asString());
        assertEquals("2025-06-10", v.get("dataInicio").asString());

        // Compõem o preço médio desta viagem: (1200 + 700) / 300 l.
        long id = v.get("id").asLong();
        finalizar(dono, id, corpo("dataFim", "2025-06-12", "kmFinalCarregado", 1500,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = buscarViagem(dono, id).get("rentabilidade");
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", r.get("origemPrecoDiesel").asString());
        dinheiro("6.3333", r.get("precoMedioDiesel"));
        dinheiro("70.00", r.get("custoArla"));
    }

    @Test
    void itemInvalidoNaoCriaNadaPelaMetade() throws Exception {
        // Preço implausível no segundo item.
        chamar(POST(VIAGENS), dono, abertura("2025-06-10", List.of(
                diesel("2025-06-01", "100", "600.00"), diesel("2025-06-02", "100", "6.00"))), 400);
        // Data no futuro.
        erro(chamar(POST(VIAGENS), dono, abertura(Datas.hoje().toString(), List.of(
                diesel(Datas.hoje().plusDays(1).toString(), "100", "600.00"))), 400), "futuro");
        // Item incompleto (validação do corpo).
        chamar(POST(VIAGENS), dono, abertura("2025-06-10", List.of(corpo("tipo", "DIESEL", "data", "2025-06-01"))), 400);
        assertEquals(0, chamar(GET(VIAGENS), dono, null, 200).size());
    }

    @Test
    void depoisDeUmaViagemSoAPartirDoFimDela() throws Exception {
        long anterior = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        diesel(dono, anterior, "2025-06-01", "100", "600.00");
        finalizar(dono, anterior, corpo("dataFim", "2025-06-05", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        String antes = buscarViagem(dono, anterior).get("rentabilidade").toString();

        erro(chamar(POST(VIAGENS), dono, abertura("2025-06-10", List.of(diesel("2025-06-04", "200", "1400.00"))), 400),
                "a partir do fim da viagem anterior");
        assertEquals(1, chamar(GET(VIAGENS), dono, null, 200).size());

        JsonNode v = chamar(POST(VIAGENS), dono, abertura("2025-06-10", List.of(diesel("2025-06-07", "200", "1400.00"))), 201);
        assertEquals("2025-06-07", v.get("abastecimentosDiesel").get(0).get("data").asString());
        // O frete anterior não absorve o custo.
        assertEquals(antes, buscarViagem(dono, anterior).get("rentabilidade").toString());
    }

    @Test
    void motoristaVinculadoAbreComAbastecimentosESemListaContinuaValendo() throws Exception {
        Motorista motorista = criarMotorista(dono, caminhaoId);
        JsonNode v = chamar(POST(VIAGENS), motorista.token(), abertura("2025-06-10",
                List.of(diesel("2025-06-08", "200", "1300.00"))), 201);
        assertEquals(motorista.id(), v.get("motoristaId").asLong());
        assertEquals(1, v.get("abastecimentosDiesel").size());
        assertTrue(v.get("rentabilidade").isNull());

        // Lançamento posterior pelo endpoint normal continua disponível.
        diesel(motorista.token(), v.get("id").asLong(), "2025-06-10", "50", "325.00");
        assertEquals(2, buscarViagem(dono, v.get("id").asLong()).get("abastecimentosDiesel").size());

        long outro = criarCaminhao(dono);
        chamar(POST(VIAGENS), dono, corpo("caminhaoId", outro, "dataInicio", "2025-06-10",
                "kmInicialCarregado", 0), 201);
    }

    @Test
    void caminhaoDeOutraEmpresaNaoRecebeNada() throws Exception {
        String outro = registrar().token();
        chamar(POST(VIAGENS), outro, abertura("2025-06-10", List.of(diesel("2025-06-08", "200", "1300.00"))), 404);
        assertEquals(0, chamar(GET(VIAGENS), dono, null, 200).size());
    }
}
