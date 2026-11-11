package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.Datas;
import tools.jackson.databind.JsonNode;

/**
 * Abastecimento feito entre duas viagens: pertence à seguinte, com a data
 * real preservada, desde que a partir do fim da anterior e antes do início
 * da seguinte. Não altera a anterior nem é contado duas vezes. No primeiro
 * frete do caminhão (sem anterior), não há limite de dias.
 */
class AbastecimentoEntreViagensTest extends ApiViagemTeste {

    private String dono;
    private long caminhaoId;
    private long anterior;
    private long seguinte;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
        anterior = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        diesel(dono, anterior, "2025-06-01", "100", "600.00");
        finalizar(dono, anterior, corpo("dataFim", "2025-06-05", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        seguinte = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-10",
                "origem", "B", "destino", "C", "valorFrete", "2000.00", "kmInicialCarregado", 600));
    }

    private JsonNode abastecer(String token, long viagem, String data, String hora, int status) throws Exception {
        return chamar(POST(VIAGENS + "/" + viagem + "/abastecimentos"), token, corpo("tipo", "DIESEL",
                "data", data, "hora", hora, "litros", "100", "valorTotal", "700.00"), status);
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    private JsonNode historica(String inicio, String fim) {
        return objectMapper.valueToTree(corpo("caminhaoId", caminhaoId, "dataInicio", inicio, "dataFim", fim,
                "origem", "X", "destino", "Y", "valorFrete", "500.00", "kmInicialCarregado", 0,
                "kmFinalCarregado", 100, "consumoMedioCarregado", "2.50"));
    }

    @Test
    void abastecimentoEntreViagensVaiParaASeguinteComDataEHoraReais() throws Exception {
        String antes = buscarViagem(dono, anterior).get("rentabilidade").toString();

        JsonNode a = abastecer(dono, seguinte, "2025-06-07", "14:30", 201);
        assertEquals("2025-06-07", a.get("data").asString());
        assertEquals("14:30:00", a.get("hora").asString());

        // A anterior não muda; a seguinte usa o preço do próprio abastecimento.
        assertEquals(antes, buscarViagem(dono, anterior).get("rentabilidade").toString());
        finalizar(dono, seguinte, corpo("dataFim", "2025-06-12", "kmFinalCarregado", 1100,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = buscarViagem(dono, seguinte).get("rentabilidade");
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", r.get("origemPrecoDiesel").asString());
        dinheiro("7.00", r.get("precoMedioDiesel"));
        // 500 km a 2,50 km/l = 200 l a R$ 7,00: o valor pago não é somado de novo ao custo.
        dinheiro("1400.00", r.get("custoDieselCarregado"));
        dinheiro("1400.00", r.get("custoOperacionalTotal"));
        assertEquals(antes, buscarViagem(dono, anterior).get("rentabilidade").toString());
    }

    @Test
    void intervaloComecaNoDiaEmQueAAnteriorTerminou() throws Exception {
        abastecer(dono, seguinte, "2025-06-05", null, 201);
        erro(abastecer(dono, seguinte, "2025-06-04", null, 400), "a partir do fim da viagem anterior");
        assertEquals(1, buscarViagem(dono, seguinte).get("abastecimentosDiesel").size());
    }

    @Test
    void primeiroFreteAceitaAbastecimentoAnteriorSemLimiteDeDias() throws Exception {
        long outro = criarCaminhao(dono);
        long primeira = criarViagem(dono, corpo("caminhaoId", outro, "dataInicio", "2025-06-10",
                "kmInicialCarregado", 0));
        JsonNode a = abastecer(dono, primeira, "2024-01-15", "07:00", 201);
        assertEquals("2024-01-15", a.get("data").asString());
        JsonNode h = chamar(POST(VIAGENS + "/historicas"), dono, corpo("caminhaoId", criarCaminhao(dono),
                "dataInicio", "2025-05-10", "dataFim", "2025-05-12", "origem", "X", "destino", "Y",
                "valorFrete", "500.00", "kmInicialCarregado", 0, "kmFinalCarregado", 100,
                "consumoMedioCarregado", "2.50", "abastecimentos", java.util.List.of(corpo("tipo", "DIESEL",
                        "data", "2025-03-01", "litros", "50", "valorTotal", "300.00"))), 201);
        assertEquals("2025-03-01", h.get("abastecimentosDiesel").get(0).get("data").asString());
    }

    @Test
    void historicaAntesDoPrimeiroFreteNaoEngoleSeusAbastecimentosAnteriores() throws Exception {
        long outro = criarCaminhao(dono);
        long primeira = criarViagem(dono, corpo("caminhaoId", outro, "dataInicio", "2025-06-10",
                "kmInicialCarregado", 0));
        abastecer(dono, primeira, "2025-06-01", null, 201);
        // Uma histórica que termina depois do abastecimento antecipado ficaria com ele no período.
        erro(chamar(POST(VIAGENS + "/historicas"), dono, corpo("caminhaoId", outro, "dataInicio", "2025-05-20",
                "dataFim", "2025-06-02", "origem", "X", "destino", "Y", "valorFrete", "500.00",
                "kmInicialCarregado", 0, "kmFinalCarregado", 100, "consumoMedioCarregado", "2.50"), 409),
                "viagem seguinte");
    }

    @Test
    void pedagioEDespesaContinuamDentroDoPeriodo() throws Exception {
        chamar(POST(VIAGENS + "/" + seguinte + "/pedagios"), dono, corpo("data", "2025-06-07", "valor", "5.00"), 400);
        chamar(POST(VIAGENS + "/" + seguinte + "/despesas-extras"), dono,
                corpo("descricao", "Lavagem", "data", "2025-06-07", "valor", "5.00"), 400);
    }

    @Test
    void anteriorNaoPassaOFimParaDepoisDoAbastecimentoDaSeguinte() throws Exception {
        abastecer(dono, seguinte, "2025-06-07", null, 201);
        String url = VIAGENS + "/" + anterior;
        erro(chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-08",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0,
                "kmFinalCarregado", 500, "consumoMedioCarregado", "2.50"), 409), "viagem seguinte");
        // Até o dia do abastecimento, pode.
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-07",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0,
                "kmFinalCarregado", 500, "consumoMedioCarregado", "2.50"), 200);
    }

    @Test
    void historicaNoIntervaloNaoEngoleOAbastecimentoDaSeguinte() throws Exception {
        abastecer(dono, seguinte, "2025-06-07", null, 201);
        erro(chamar(POST(VIAGENS + "/historicas"), dono, historica("2025-06-06", "2025-06-08"), 409),
                "viagem seguinte");
        chamar(POST(VIAGENS + "/historicas"), dono, historica("2025-06-06", "2025-06-07"), 201);
    }

    @Test
    void historicaAceitaAbastecimentoDepoisDaAnterior() throws Exception {
        // Fecha a seguinte para abrir espaço depois dela.
        finalizar(dono, seguinte, corpo("dataFim", "2025-06-12", "kmFinalCarregado", 1100,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode v = chamar(POST(VIAGENS + "/historicas"), dono, corpo("caminhaoId", caminhaoId,
                "dataInicio", "2025-06-20", "dataFim", "2025-06-22", "origem", "X", "destino", "Y",
                "valorFrete", "500.00", "kmInicialCarregado", 1200, "kmFinalCarregado", 1300,
                "consumoMedioCarregado", "2.50", "abastecimentos", java.util.List.of(corpo("tipo", "DIESEL",
                        "data", "2025-06-15", "hora", "08:00", "litros", "50", "valorTotal", "300.00"))), 201);
        assertEquals("2025-06-15", v.get("abastecimentosDiesel").get(0).get("data").asString());
        dinheiro("6.00", v.get("rentabilidade").get("precoMedioDiesel"));
    }

    @Test
    void mudarOInicioReavaliaOsAbastecimentos() throws Exception {
        abastecer(dono, seguinte, "2025-06-07", null, 201);
        String url = VIAGENS + "/" + seguinte;
        // Início mais cedo: o abastecimento passa a estar dentro do período.
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-06", "origem", "B", "destino", "C",
                "valorFrete", "2000.00", "kmInicialCarregado", 600), 200);
        // Início antes do fim da anterior: sobreposição.
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-04", "origem", "B", "destino", "C",
                "valorFrete", "2000.00", "kmInicialCarregado", 600), 409);
    }

    @Test
    void motoristaVinculadoRegistraAbastecimentoEntreViagens() throws Exception {
        Motorista motorista = criarMotorista(dono, caminhaoId);
        abastecer(motorista.token(), seguinte, "2025-06-08", "06:15", 201);
    }

    @Test
    void horarioNoFuturoERecusado() throws Exception {
        LocalTime daquiAUmaHora = LocalTime.now(Datas.FUSO).plusHours(1);
        Assumptions.assumeTrue(daquiAUmaHora.isAfter(LocalTime.of(1, 0)), "perto da meia-noite");
        long outro = criarCaminhao(dono);
        long hoje = criarViagem(dono, corpo("caminhaoId", outro, "dataInicio", Datas.hoje().toString(),
                "kmInicialCarregado", 0));
        erro(abastecer(dono, hoje, Datas.hoje().toString(), daquiAUmaHora.withNano(0).toString(), 400), "futuro");
    }
}
