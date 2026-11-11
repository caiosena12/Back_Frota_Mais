package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Viagens canceladas no financeiro: entram no mês da data do cancelamento,
 * em bloco separado dos fretes concluídos, com os valores reais (recebido no
 * campo do frete, despesas pelos lançamentos). Só o DONO registra depois do
 * cancelamento. Nada é contado duas vezes.
 */
class CanceladasFinanceiroTest extends ApiViagemTeste {

    private String dono;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
    }

    private long abrir(long caminhao, String inicio) throws Exception {
        return criarViagem(dono, corpo("caminhaoId", caminhao, "dataInicio", inicio, "origem", "A",
                "destino", "B", "kmInicialCarregado", 1000));
    }

    private JsonNode cancelar(long id, String data) throws Exception {
        return chamar(POST(VIAGENS + "/" + id + "/cancelar"), dono, corpo("motivo", "Cliente desistiu",
                "dataFim", data), 200);
    }

    private JsonNode indicadores(String filtro) throws Exception {
        return chamar(GET(VIAGENS + "/indicadores?" + filtro), dono, null, 200);
    }

    private void informarRecebido(long id, String inicio, String fim, String valor) throws Exception {
        chamar(PUT(VIAGENS + "/" + id), dono, corpo("dataInicio", inicio, "dataFim", fim, "origem", "A",
                "destino", "B", "valorFrete", valor, "kmInicialCarregado", 1000), 200);
    }

    @Test
    void exemploRecebido500Despesas200Resultado300NoMesDoCancelamento() throws Exception {
        long id = abrir(caminhaoId, "2025-06-28");
        JsonNode cancelada = cancelar(id, "2025-07-02");   // virada de mês: vale julho
        assertEquals("CANCELADA", cancelada.get("status").asString());

        // Depois do cancelamento, o dono registra o recebido e a despesa nos campos normais.
        informarRecebido(id, "2025-06-28", "2025-07-02", "500.00");
        despesaExtra(dono, id, "Descarga parcial e taxa", "2025-07-01", "200.00");
        JsonNode v = buscarViagem(dono, id);
        assertEquals("CANCELADA", v.get("status").asString());   // receita não muda o status

        JsonNode julho = indicadores("ano=2025&mes=7");
        assertEquals(1, julho.get("viagensCanceladas").asInt());
        dinheiro("500.00", julho.get("receitaCanceladas"));
        dinheiro("200.00", julho.get("custoCanceladas"));
        dinheiro("300.00", julho.get("resultadoCanceladas"));
        dinheiro("300.00", julho.get("resultadoFinanceiroPeriodo"));
        // O diesel desta não pôde ser calculado (sem km final): listada, não tratada como zero.
        assertEquals(id, julho.get("canceladasComCustoIncompleto").get(0).asLong());
        // Não é frete concluído.
        assertEquals(0, julho.get("viagensFinalizadas").asInt());
        dinheiro("0.00", julho.get("receitaTotal"));

        JsonNode junho = indicadores("ano=2025&mes=6");
        assertEquals(0, junho.get("viagensCanceladas").asInt());
        dinheiro("0.00", junho.get("resultadoFinanceiroPeriodo"));
    }

    @Test
    void resultadoGeralSomaConcluidosCanceladasEManutencoesSemDuplaContagem() throws Exception {
        // Frete concluído em junho: receita 3000, diesel 500 km / 2,50 x 6,00 = 1200, lucro 1800.
        long concluida = abrir(caminhaoId, "2025-06-01");
        diesel(dono, concluida, "2025-06-01", "100", "600.00");
        chamar(PUT(VIAGENS + "/" + concluida), dono, corpo("dataInicio", "2025-06-01", "origem", "A",
                "destino", "B", "valorFrete", "3000.00", "kmInicialCarregado", 1000), 200);
        finalizar(dono, concluida, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 1500,
                "consumoMedioCarregado", "2.50"), 200);

        // Cancelada em junho com custo completo: 100 km / 2,50 x 6,00 = 240 de diesel + 60 de pedágio.
        long cancelada = abrir(caminhaoId, "2025-06-10");
        pedagio(dono, cancelada, "2025-06-10", "60.00");
        chamar(POST(VIAGENS + "/" + cancelada + "/cancelar"), dono, corpo("motivo", "Carga recusada",
                "dataFim", "2025-06-11", "kmFinalCarregado", 1100, "consumoMedioCarregado", "2.50"), 200);
        informarRecebido(cancelada, "2025-06-10", "2025-06-11", "400.00");
        // O PUT da cancelada exige a km final e o consumo para manter o diesel calculado.
        chamar(PUT(VIAGENS + "/" + cancelada), dono, corpo("dataInicio", "2025-06-10", "dataFim", "2025-06-11",
                "origem", "A", "destino", "B", "valorFrete", "400.00", "kmInicialCarregado", 1000,
                "kmFinalCarregado", 1100, "consumoMedioCarregado", "2.50"), 200);

        chamar(POST("/api/manutencoes"), dono, corpo("caminhaoId", caminhaoId, "data", "2025-06-20",
                "descricao", "Revisão", "valor", "100.00"), 201);

        JsonNode i = indicadores("ano=2025&mes=6");
        assertEquals(1, i.get("viagensFinalizadas").asInt());
        dinheiro("3000.00", i.get("receitaTotal"));
        dinheiro("1800.00", i.get("lucroTotal"));
        assertEquals(1, i.get("viagensCanceladas").asInt());
        assertEquals(0, i.get("canceladasComCustoIncompleto").size());
        dinheiro("400.00", i.get("receitaCanceladas"));
        dinheiro("300.00", i.get("custoCanceladas"));
        dinheiro("100.00", i.get("resultadoCanceladas"));
        dinheiro("1700.00", i.get("resultadoAposManutencoes"));       // só concluídos − manutenção
        dinheiro("1800.00", i.get("resultadoFinanceiroPeriodo"));     // 1800 + 100 − 100
        // Indicadores de desempenho dos concluídos não mudam com a cancelada.
        assertEquals(500, i.get("kmCarregado").asLong());
        dinheiro("60.00", i.get("margemPercentual"));
    }

    @Test
    void porCaminhaoMostraCaminhaoSoComCancelada() throws Exception {
        long outro = criarCaminhao(dono);
        long id = abrir(outro, "2025-08-01");
        cancelar(id, "2025-08-02");
        informarRecebido(id, "2025-08-01", "2025-08-02", "150.00");

        JsonNode lista = chamar(GET(VIAGENS + "/indicadores/por-caminhao?ano=2025&mes=8"), dono, null, 200);
        assertEquals(1, lista.size());
        assertEquals(outro, lista.get(0).get("caminhaoId").asLong());
        JsonNode i = lista.get(0).get("indicadores");
        assertEquals(0, i.get("viagensFinalizadas").asInt());
        assertEquals(1, i.get("viagensCanceladas").asInt());
        dinheiro("150.00", i.get("receitaCanceladas"));

        JsonNode filtrado = indicadores("ano=2025&mes=8&caminhaoId=" + caminhaoId);
        assertEquals(0, filtrado.get("viagensCanceladas").asInt());
    }

    @Test
    void inclusaoDoDonoEmCanceladaEAuditadaEArquivaAFotografia() throws Exception {
        long id = abrir(caminhaoId, "2025-06-01");
        cancelar(id, "2025-06-03");
        long despesa = despesaExtra(dono, id, "Taxa de cancelamento", "2025-06-02", "80.00").get("id").asLong();

        JsonNode alteracoes = chamar(GET(VIAGENS + "/" + id + "/alteracoes"), dono, null, 200);
        assertTrue(alteracoes.size() >= 3, alteracoes::toString);
        for (JsonNode a : alteracoes) {
            assertEquals("DESPESA_EXTRA", a.get("entidade").asString());
            assertEquals(despesa, a.get("registroId").asLong());
            assertTrue(a.get("valorAnterior").isNull());
        }
        dinheiro("80.00", buscarViagem(dono, id).get("rentabilidade").get("despesasExtras"));
        assertEquals(1, chamar(GET(VIAGENS + "/" + id + "/fotografias-anteriores"), dono, null, 200).size());

        // Fora do período da cancelada: recusado. Viagem finalizada continua sem inclusão.
        chamar(POST(VIAGENS + "/" + id + "/pedagios"), dono, corpo("data", "2025-06-04", "valor", "1.00"), 400);
    }

    @Test
    void finalizadaContinuaSemLancamentoNovoESemCancelamento() throws Exception {
        long id = abrir(caminhaoId, "2025-06-01");
        chamar(PUT(VIAGENS + "/" + id), dono, corpo("dataInicio", "2025-06-01", "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 1000), 200);
        diesel(dono, id, "2025-06-01", "100", "600.00");
        finalizar(dono, id, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 1500,
                "consumoMedioCarregado", "2.50"), 200);
        chamar(POST(VIAGENS + "/" + id + "/pedagios"), dono, corpo("data", "2025-06-02", "valor", "1.00"), 409);
        chamar(POST(VIAGENS + "/" + id + "/cancelar"), dono, corpo("motivo", "x"), 409);
    }
}
