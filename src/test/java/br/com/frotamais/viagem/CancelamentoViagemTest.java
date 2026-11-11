package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.Datas;
import tools.jackson.databind.JsonNode;

/**
 * Cancelamento de viagem: só o DONO, com motivo obrigatório. Nada é apagado;
 * a viagem fica CANCELADA, com período fechado, lançamentos preservados e
 * resultado consolidado. Depois, como uma finalizada, só aceita correções do
 * DONO. Não entra nos indicadores de fretes concluídos.
 */
class CancelamentoViagemTest extends ApiViagemTeste {

    private String dono;
    private long caminhaoId;
    private long viagem;
    private String url;
    private long pedagioId;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
        viagem = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "kmInicialCarregado", 1000));
        url = VIAGENS + "/" + viagem;
        diesel(dono, viagem, "2025-06-01", "100", "600.00");
        pedagioId = pedagio(dono, viagem, "2025-06-02", "40.00").get("id").asLong();
    }

    private JsonNode cancelar(String token, Object corpo, int status) throws Exception {
        return chamar(POST(url + "/cancelar"), token, corpo, status);
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    private void continuaEmAndamento() throws Exception {
        JsonNode v = buscarViagem(dono, viagem);
        assertEquals("EM_ANDAMENTO", v.get("status").asString());
        assertTrue(v.get("dataFim").isNull(), v::toString);
        assertTrue(v.get("motivoCancelamento").isNull(), v::toString);
    }

    @Test
    void donoCancelaComMotivoEPreservaTudo() throws Exception {
        JsonNode v = cancelar(dono, corpo("motivo", "  Cliente desistiu da carga  ", "dataFim", "2025-06-03",
                "kmFinalCarregado", 1200, "consumoMedioCarregado", "2.50"), 200);

        assertEquals("CANCELADA", v.get("status").asString());
        assertEquals("Cliente desistiu da carga", v.get("motivoCancelamento").asString());
        assertFalse(v.get("canceladaEm").isNull(), v::toString);
        assertEquals("2025-06-03", v.get("dataFim").asString());
        assertTrue(v.get("finalizadaEm").isNull(), v::toString);
        assertEquals(1, v.get("abastecimentosDiesel").size());
        assertEquals(1, v.get("pedagios").size());

        // Resultado consolidado com a mesma calculadora: 200 km a 2,50 km/l = 80 l a R$ 6,00.
        JsonNode r = v.get("rentabilidade");
        dinheiro("480.00", r.get("custoDieselCarregado"));
        dinheiro("40.00", r.get("pedagios"));
        // Sem valor recebido informado, a receita não é inventada.
        assertTrue(r.get("receita").isNull(), r::toString);

        // Igual ao consultar de novo, e listado como cancelada.
        assertEquals(r, buscarViagem(dono, viagem).get("rentabilidade"));
        JsonNode canceladas = chamar(GET(VIAGENS + "?status=CANCELADA"), dono, null, 200);
        assertEquals(1, canceladas.size());
        assertEquals(viagem, canceladas.get(0).get("id").asLong());
    }

    @Test
    void motivoEObrigatorio() throws Exception {
        erro(cancelar(dono, corpo("dataFim", "2025-06-03"), 400), "motivo");
        erro(cancelar(dono, corpo("motivo", "   "), 400), "motivo");
        continuaEmAndamento();
    }

    @Test
    void motoristaNaoCancelaNemOutraEmpresa() throws Exception {
        Motorista motorista = criarMotorista(dono, caminhaoId);
        chamar(GET(url), motorista.token(), null, 200);
        cancelar(motorista.token(), corpo("motivo", "Quebrou"), 403);

        cancelar(registrar().token(), corpo("motivo", "Quebrou"), 404);
        continuaEmAndamento();
    }

    @Test
    void dataFinalPadraoEHojeEPrecisaCobrirOsLancamentos() throws Exception {
        erro(cancelar(dono, corpo("motivo", "x", "dataFim", "2025-06-01"), 400), "fora do período");
        erro(cancelar(dono, corpo("motivo", "x", "dataFim", "2025-05-31"), 400), "anterior");
        erro(cancelar(dono, corpo("motivo", "x", "dataFim", Datas.hoje().plusDays(1).toString()), 400), "futuro");
        erro(cancelar(dono, corpo("motivo", "x", "kmFinalCarregado", 900), 400), "quilometragem final");
        continuaEmAndamento();

        JsonNode v = cancelar(dono, corpo("motivo", "Carga retida"), 200);
        assertEquals(Datas.hoje().toString(), v.get("dataFim").asString());
    }

    @Test
    void periodoFechadoLiberaOCaminhao() throws Exception {
        cancelar(dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-03"), 200);
        // A próxima pode começar no dia em que a cancelada terminou.
        criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-03", "kmInicialVazio", 1100));
    }

    @Test
    void naoCancelaDuasVezesNemFinalizaCancelada() throws Exception {
        cancelar(dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-03"), 200);
        erro(cancelar(dono, corpo("motivo", "de novo"), 409), "já está cancelada");
        erro(finalizar(dono, viagem, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 1200,
                "consumoMedioCarregado", "2.50"), 409), "cancelada");
        assertEquals("Carga retida", buscarViagem(dono, viagem).get("motivoCancelamento").asString());
    }

    @Test
    void viagemFinalizadaNaoECancelada() throws Exception {
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 1000), 200);
        finalizar(dono, viagem, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 1200,
                "consumoMedioCarregado", "2.50"), 200);
        erro(cancelar(dono, corpo("motivo", "tarde demais"), 409), "finalizada");
        assertEquals("FINALIZADA", buscarViagem(dono, viagem).get("status").asString());
    }

    @Test
    void canceladaSoAceitaCorrecoesDoDono() throws Exception {
        Motorista motorista = criarMotorista(dono, caminhaoId);
        cancelar(dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-03",
                "kmFinalCarregado", 1200, "consumoMedioCarregado", "2.50"), 200);

        // Exclusão de lançamento: ninguém. Lançamento novo: só o dono.
        erro(chamar(DELETE(url + "/pedagios/" + pedagioId), dono, null, 409), "cancelada");
        chamar(POST(url + "/pedagios"), motorista.token(), corpo("data", "2025-06-02", "valor", "5.00"), 403);
        chamar(POST(url + "/despesas-extras"), motorista.token(), corpo("descricao", "x", "data", "2025-06-02",
                "valor", "5.00"), 403);
        chamar(POST(url + "/abastecimentos"), motorista.token(), corpo("tipo", "DIESEL", "data", "2025-06-02",
                "litros", "10", "valorTotal", "60.00"), 403);
        // Motorista não altera viagem encerrada.
        chamar(PUT(url + "/pedagios/" + pedagioId), motorista.token(),
                corpo("data", "2025-06-02", "valor", "1.00"), 403);
        chamar(PUT(url), motorista.token(), corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-03",
                "kmInicialCarregado", 1000), 403);

        // DONO corrige um lançamento: a fotografia é regravada só desta viagem.
        chamar(PUT(url + "/pedagios/" + pedagioId), dono, corpo("data", "2025-06-02", "valor", "55.00"), 200);
        dinheiro("55.00", buscarViagem(dono, viagem).get("rentabilidade").get("pedagios"));

        // DONO registra o valor recebido no campo normal do frete; a data final continua obrigatória.
        erro(chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "origem", "A", "destino", "B",
                "valorFrete", "300.00", "kmInicialCarregado", 1000), 400), "data final");
        JsonNode v = chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-03",
                "origem", "A", "destino", "B", "valorFrete", "300.00", "kmInicialCarregado", 1000,
                "kmFinalCarregado", 1200, "consumoMedioCarregado", "2.50"), 200);
        assertEquals("CANCELADA", v.get("status").asString());
        dinheiro("300.00", v.get("rentabilidade").get("receita"));
        dinheiro("-235.00", v.get("rentabilidade").get("lucro"));  // 300 - 480 diesel - 55 pedágio
    }

    @Test
    void canceladaNaoEntraNosIndicadoresDeFretesConcluidos() throws Exception {
        cancelar(dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-03",
                "kmFinalCarregado", 1200, "consumoMedioCarregado", "2.50"), 200);
        JsonNode ind = chamar(GET(VIAGENS + "/indicadores?de=2025-06-01&ate=2025-06-30"), dono, null, 200);
        assertEquals(0, ind.get("viagensFinalizadas").asInt());
        assertEquals(0, ind.get("viagensConsideradas").asInt());
    }

    @Test
    void caminhaoPodeSerExcluidoDepoisDoCancelamento() throws Exception {
        chamar(DELETE("/api/caminhoes/" + caminhaoId), dono, null, 409);
        cancelar(dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-03"), 200);
        chamar(DELETE("/api/caminhoes/" + caminhaoId), dono, null, 204);
        assertEquals("CANCELADA", buscarViagem(dono, viagem).get("status").asString());
    }
}
