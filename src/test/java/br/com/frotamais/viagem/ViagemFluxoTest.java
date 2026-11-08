package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Fluxo do frete com o exemplo da especificação:
 * Frete 1: Cruzália → Santos, carregado.
 * Frete 2: Santos → São Paulo vazio, São Paulo → Palmital carregado.
 */
class ViagemFluxoTest extends ApiViagemTeste {

    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
    }

    /** Frete 1 finalizado: 400 km carregado (10.000 → 10.400), diesel a R$ 6,00/L. */
    private long frete1Finalizado() throws Exception {
        long id = criarViagem(token, corpo(
                "caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "Cruzália", "destino", "Santos",
                "valorFrete", "2800.00", "kmInicialCarregado", 10000));
        diesel(token, id, "2025-03-03", "150", "900.00");
        finalizar(token, id, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 10400,
                "consumoMedioCarregado", "2.50"), 200);
        return id;
    }

    /** Frete 2 aberto em Santos, ainda vazio, a partir do km de descarga do Frete 1. */
    private long frete2Aberto() throws Exception {
        return criarViagem(token, corpo(
                "caminhaoId", caminhaoId, "dataInicio", "2025-03-05", "kmInicialVazio", 10400));
    }

    @Test
    void cadastraEConsultaFrete() throws Exception {
        JsonNode criada = chamar(POST(VIAGENS), token, corpo(
                "caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "  Cruzália ", "destino", "Santos",
                "valorFrete", "2800.00", "kmInicialCarregado", 10000), 201);

        long id = criada.get("id").asLong();
        assertEquals("EM_ANDAMENTO", criada.get("status").asString());
        assertEquals("Cruzália", criada.get("origem").asString());
        assertEquals(caminhaoId, criada.get("caminhaoId").asLong());
        assertFalse(criada.get("rentabilidade").get("calculoCompleto").asBoolean());

        JsonNode detalhe = buscarViagem(token, id);
        assertEquals(10000, detalhe.get("kmInicialCarregado").asInt());
        assertTrue(detalhe.get("kmFinalVazio").isNull());
        dinheiro("2800.00", detalhe.get("valorFrete"));

        JsonNode lista = chamar(GET(VIAGENS), token, null, 200);
        assertEquals(1, lista.size());
        assertEquals(id, lista.get(0).get("id").asLong());

        assertEquals(1, chamar(GET(VIAGENS + "?status=EM_ANDAMENTO"), token, null, 200).size());
        assertEquals(0, chamar(GET(VIAGENS + "?status=FINALIZADA"), token, null, 200).size());
        assertEquals(1, chamar(GET(VIAGENS + "?caminhaoId=" + caminhaoId), token, null, 200).size());
    }

    @Test
    void finalizaFreteECalculaRentabilidade() throws Exception {
        long id = frete1Finalizado();
        JsonNode v = buscarViagem(token, id);

        assertEquals("FINALIZADA", v.get("status").asString());
        assertEquals("2025-03-04", v.get("dataFim").asString());
        assertFalse(v.get("finalizadaEm").isNull());

        JsonNode r = v.get("rentabilidade");
        assertTrue(r.get("calculoCompleto").asBoolean(), r.toString());
        assertEquals(0, r.get("kmVazio").asInt());
        assertEquals(400, r.get("kmCarregado").asInt());
        dinheiro("6.00", r.get("precoMedioDiesel"));
        dinheiro("0.00", r.get("custoDieselVazio"));
        dinheiro("960.00", r.get("custoDieselCarregado"));   // 400 ÷ 2,5 = 160 L × 6,00
        dinheiro("960.00", r.get("custoOperacionalTotal"));
        dinheiro("1840.00", r.get("lucro"));
        dinheiro("65.71", r.get("margemPercentual"));

        // Finalizar de novo é conflito.
        finalizar(token, id, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 10500,
                "consumoMedioCarregado", "2.50"), 409);
    }

    @Test
    void deslocamentoVazioEAtribuidoAoProximoFrete() throws Exception {
        long frete1 = frete1Finalizado();
        long frete2 = frete2Aberto();

        // Pedágio no trecho vazio (Santos → São Paulo): lançado no Frete 2.
        pedagio(token, frete2, "2025-03-05", "25.40");

        // Carregou em São Paulo, no km 10.480: fim do vazio e início do carregado.
        chamar(PUT(VIAGENS + "/" + frete2), token, corpo(
                "dataInicio", "2025-03-05", "origem", "São Paulo", "destino", "Palmital",
                "valorFrete", "3500.00", "kmInicialVazio", 10400, "kmInicialCarregado", 10480), 200);
        pedagio(token, frete2, "2025-03-06", "40.10");
        diesel(token, frete2, "2025-03-06", "100", "640.00");

        JsonNode v = finalizar(token, frete2, corpo("dataFim", "2025-03-07", "kmFinalCarregado", 10980,
                "consumoMedioVazio", "3.20", "consumoMedioCarregado", "2.50"), 200);

        assertEquals(10400, v.get("kmInicialVazio").asInt());
        assertEquals(10480, v.get("kmFinalVazio").asInt());
        JsonNode r = v.get("rentabilidade");
        assertEquals(80, r.get("kmVazio").asInt());
        assertEquals(500, r.get("kmCarregado").asInt());
        dinheiro("25", r.get("litrosEstimadosVazio"));       // 80 ÷ 3,2
        dinheiro("200", r.get("litrosEstimadosCarregado"));  // 500 ÷ 2,5
        dinheiro("160.00", r.get("custoDieselVazio"));       // 25 L × 6,40
        dinheiro("1280.00", r.get("custoDieselCarregado"));  // 200 L × 6,40
        dinheiro("65.50", r.get("pedagios"));
        dinheiro("1505.50", r.get("custoOperacionalTotal"));
        dinheiro("1994.50", r.get("lucro"));

        // O Frete 1 não absorve nada do vazio posterior.
        JsonNode r1 = buscarViagem(token, frete1).get("rentabilidade");
        assertEquals(0, r1.get("kmVazio").asInt());
        dinheiro("0.00", r1.get("custoDieselVazio"));
        dinheiro("0.00", r1.get("pedagios"));
        dinheiro("960.00", r1.get("custoOperacionalTotal"));
    }

    @Test
    void abastecimentosParciaisFormamPrecoMedioPonderado() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-04-01",
                "origem", "A", "destino", "B", "valorFrete", "5000.00", "kmInicialCarregado", 0));
        diesel(token, id, "2025-04-01", "100", "600.00");   // R$ 6,00/L
        diesel(token, id, "2025-04-02", "200", "1260.00");  // R$ 6,30/L
        // Média simples seria 6,15; a ponderada é 1.860 ÷ 300 = 6,20.

        JsonNode v = finalizar(token, id, corpo("dataFim", "2025-04-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = v.get("rentabilidade");

        assertEquals(2, v.get("abastecimentosDiesel").size());
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", r.get("origemPrecoDiesel").asString());
        dinheiro("6.20", r.get("precoMedioDiesel"));
        // 200 L estimados × 6,20, e não os R$ 1.860,00 pagos.
        dinheiro("1240.00", r.get("custoDieselCarregado"));
        dinheiro("1240.00", r.get("custoOperacionalTotal"));
    }

    @Test
    void semAbastecimentoUsaUltimoPrecoConhecidoDoCaminhao() throws Exception {
        long frete1 = frete1Finalizado();
        long frete2 = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "origem", "Santos", "destino", "Palmital", "valorFrete", "3000.00",
                "kmInicialCarregado", 10400));

        JsonNode r = finalizar(token, frete2, corpo("dataFim", "2025-03-06", "kmFinalCarregado", 10900,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");

        assertEquals("ULTIMO_PRECO_DO_CAMINHAO", r.get("origemPrecoDiesel").asString());
        assertEquals(frete1, r.get("viagemReferenciaPrecoDieselId").asLong());
        dinheiro("6.00", r.get("precoMedioDiesel"));
        dinheiro("1200.00", r.get("custoDieselCarregado"));  // 200 L × 6,00
        assertTrue(r.get("calculoCompleto").asBoolean());
    }

    @Test
    void ultimoPrecoNaoVemDeOutroCaminhaoNemDeViagemPosterior() throws Exception {
        // Outro caminhão da mesma empresa tem diesel: não serve de referência.
        long outroCaminhao = criarCaminhao(token);
        long outra = criarViagem(token, corpo("caminhaoId", outroCaminhao, "dataInicio", "2025-01-10",
                "kmInicialCarregado", 0));
        diesel(token, outra, "2025-01-10", "100", "500.00");

        long anterior = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-02-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        JsonNode r = finalizar(token, anterior, corpo("dataFim", "2025-02-02", "kmFinalCarregado", 100,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        assertEquals("SEM_REFERENCIA", r.get("origemPrecoDiesel").asString());

        // Uma viagem posterior do mesmo caminhão, com diesel, não muda o passado.
        long posterior = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-02-10",
                "kmInicialCarregado", 100));
        diesel(token, posterior, "2025-02-10", "100", "700.00");
        assertEquals("SEM_REFERENCIA",
                buscarViagem(token, anterior).get("rentabilidade").get("origemPrecoDiesel").asString());
    }

    @Test
    void semHistoricoDePrecoNaoApresentaCustoEstimado() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "Cruzália", "destino", "Santos", "valorFrete", "2800.00", "kmInicialCarregado", 0));
        pedagio(token, id, "2025-03-03", "30.00");

        JsonNode v = finalizar(token, id, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = v.get("rentabilidade");

        assertEquals("FINALIZADA", v.get("status").asString());
        assertEquals("SEM_REFERENCIA", r.get("origemPrecoDiesel").asString());
        assertTrue(r.get("precoMedioDiesel").isNull());
        assertTrue(r.get("custoDieselCarregado").isNull());
        assertTrue(r.get("custoOperacionalTotal").isNull());
        assertTrue(r.get("lucro").isNull());
        assertTrue(r.get("margemPercentual").isNull());
        assertFalse(r.get("calculoCompleto").asBoolean());
        assertTrue(r.get("pendencias").toString().contains("Sem referência de preço do diesel"));
        dinheiro("160", r.get("litrosEstimadosCarregado"));
    }

    @Test
    void arlaEOpcionalEEntraComoCustoSemAfetarODiesel() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, id, "2025-03-03", "100", "600.00");
        JsonNode semArla = finalizar(token, id, corpo("dataFim", "2025-03-03", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        dinheiro("0.00", semArla.get("custoArla"));
        assertTrue(semArla.get("calculoCompleto").asBoolean());

        // Outro frete igual, agora com Arla: soma ao custo e não muda o preço do diesel.
        long comArlaId = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-04",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, comArlaId, "2025-03-04", "100", "600.00");
        arla(token, comArlaId, "2025-03-04", "40", "150.00");
        JsonNode v = finalizar(token, comArlaId, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode comArla = v.get("rentabilidade");
        assertEquals(1, v.get("abastecimentosArla").size());
        assertEquals(1, v.get("abastecimentosDiesel").size());
        dinheiro("6.00", comArla.get("precoMedioDiesel"));
        dinheiro("150.00", comArla.get("custoArla"));
        dinheiro(semArla.get("custoDieselCarregado").asString(), comArla.get("custoDieselCarregado"));
        dinheiro("1110.00", comArla.get("custoOperacionalTotal"));   // 960 + 150
    }

    @Test
    void pedagiosEDespesasExtrasEntramNoCusto() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, id, "2025-03-03", "100", "600.00");
        long p1 = pedagio(token, id, "2025-03-03", "12.30").get("id").asLong();
        pedagio(token, id, "2025-03-03", "12.30");   // mesma praça na volta: não é duplicação indevida
        JsonNode extra = despesaExtra(token, id, " Lavagem ", "2025-03-03", "80.00");
        assertEquals("Lavagem", extra.get("descricao").asString());

        // Correção de um pedágio.
        chamar(PUT(VIAGENS + "/" + id + "/pedagios/" + p1), token, corpo("data", "2025-03-03", "valor", "15.00"), 200);
        // Exclusão permitida com a viagem em andamento.
        long p3 = pedagio(token, id, "2025-03-03", "99.99").get("id").asLong();
        chamar(DELETE(VIAGENS + "/" + id + "/pedagios/" + p3), token, null, 204);

        JsonNode v = finalizar(token, id, corpo("dataFim", "2025-03-03", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = v.get("rentabilidade");

        assertEquals(2, v.get("pedagios").size());
        assertEquals(1, v.get("despesasExtras").size());
        dinheiro("27.30", r.get("pedagios"));
        dinheiro("80.00", r.get("despesasExtras"));
        dinheiro("1067.30", r.get("custoOperacionalTotal"));  // 960 + 27,30 + 80
        dinheiro("932.70", r.get("lucro"));
    }

    @Test
    void manutencaoNaoFazParteDaRentabilidadeDoFrete() throws Exception {
        long id = frete1Finalizado();
        JsonNode r = buscarViagem(token, id).get("rentabilidade");

        // Não há campo de manutenção, e o custo total é exatamente a soma dos custos do frete.
        assertFalse(r.has("manutencao"));
        assertFalse(r.has("custoManutencao"));
        var soma = r.get("custoDieselVazio").decimalValue()
                .add(r.get("custoDieselCarregado").decimalValue())
                .add(r.get("custoArla").decimalValue())
                .add(r.get("pedagios").decimalValue())
                .add(r.get("despesasExtras").decimalValue());
        dinheiro(soma.toPlainString(), r.get("custoOperacionalTotal"));
    }

    @Test
    void valoresCalculadosEEmpresaEnviadosPeloClienteSaoIgnorados() throws Exception {
        Map<String, Object> corpo = corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0,
                "empresaId", 999999, "status", "FINALIZADA", "lucro", "999999.00",
                "custoOperacionalTotal", "0.00");
        JsonNode v = chamar(POST(VIAGENS), token, corpo, 201);

        assertEquals("EM_ANDAMENTO", v.get("status").asString());
        assertTrue(v.get("rentabilidade").get("lucro").isNull());
        assertFalse(v.has("empresaId"));
    }

    @Test
    void donoCorrigeViagemFinalizadaMantendoCoerencia() throws Exception {
        long id = frete1Finalizado();

        JsonNode v = chamar(PUT(VIAGENS + "/" + id), token, corpo(
                "dataInicio", "2025-03-03", "origem", "Cruzália", "destino", "Santos",
                "valorFrete", "3000.00", "kmInicialCarregado", 10000, "consumoMedioCarregado", "2.50",
                "dataFim", "2025-03-04", "kmFinalCarregado", 10400), 200);
        assertEquals("FINALIZADA", v.get("status").asString());
        dinheiro("2040.00", v.get("rentabilidade").get("lucro"));

        // Apagar dados obrigatórios de uma viagem finalizada é recusado.
        chamar(PUT(VIAGENS + "/" + id), token, corpo(
                "dataInicio", "2025-03-03", "origem", "Cruzália", "destino", "Santos",
                "kmInicialCarregado", 10000, "consumoMedioCarregado", "2.50",
                "dataFim", "2025-03-04", "kmFinalCarregado", 10400), 400);
        dinheiro("3000.00", buscarViagem(token, id).get("valorFrete"));
    }

    @Test
    void novaViagemDoCaminhaoSoDepoisDeFinalizarAAnterior() throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-04",
                "kmInicialVazio", 100), 409);

        finalizar(token, id, corpo("dataFim", "2025-03-03", "kmFinalCarregado", 100,
                "consumoMedioCarregado", "2.50"), 200);
        criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-04", "kmInicialVazio", 100));
    }
}
