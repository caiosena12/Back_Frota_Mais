package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Regressão A1 da auditoria: duas viagens do mesmo caminhão não podem ter
 * períodos sobrepostos. Datas inclusivas; viagem em andamento tem período aberto.
 * O odômetro NÃO precisa ser contínuo entre fretes (o motorista zera os contadores).
 */
class SobreposicaoViagemTest extends ApiViagemTeste {

    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
    }

    private long finalizada(String inicio, String fim) throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        finalizar(token, id, corpo("dataFim", fim, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200);
        return id;
    }

    /** PUT de correção de viagem finalizada, mudando só as datas. */
    private JsonNode moverDatas(long id, String inicio, String fim, int status) throws Exception {
        return chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", inicio, "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 0, "consumoMedioCarregado", "2.50",
                "dataFim", fim, "kmFinalCarregado", 400), status);
    }

    private void sobreposicao(JsonNode erro) {
        assertTrue(erro.get("detalhes").toString().contains("sobrepõe"), erro::toString);
    }

    @Test
    void alteracaoDeDatasQueProduzSobreposicaoERecusada() throws Exception {
        finalizada("2025-03-01", "2025-03-10");
        long z = finalizada("2025-03-15", "2025-03-20");

        sobreposicao(moverDatas(z, "2025-03-01", "2025-03-10", 409));   // totalmente sobreposta (mesmo período)
        sobreposicao(moverDatas(z, "2025-03-03", "2025-03-08", 409));   // dentro da outra
        sobreposicao(moverDatas(z, "2025-02-20", "2025-03-25", 409));   // contém a outra
        sobreposicao(moverDatas(z, "2025-03-08", "2025-03-20", 409));   // parcial
        sobreposicao(moverDatas(z, "2025-03-10", "2025-03-20", 409));   // compartilha um dia

        // A recusa não altera nada.
        assertEquals("2025-03-15", buscarViagem(token, z).get("dataInicio").asString());

        // Sem sobreposição: aceito, inclusive no dia seguinte ao fim da outra.
        moverDatas(z, "2025-03-11", "2025-03-20", 200);
    }

    @Test
    void novaViagemNaoPodeComecarDentroDoPeriodoDeOutra() throws Exception {
        finalizada("2025-03-01", "2025-03-10");
        finalizada("2025-03-15", "2025-03-20");

        // Uma viagem nova está em andamento: período aberto, então cruza qualquer viagem posterior.
        for (String inicio : new String[] {"2025-03-05", "2025-03-10", "2025-03-12", "2025-02-01", "2025-03-20"}) {
            sobreposicao(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                    "kmInicialVazio", 0), 409));
        }
        assertEquals(2, chamar(GET(VIAGENS), token, null, 200).size());

        criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-21", "kmInicialVazio", 0));
    }

    @Test
    void viagemEmAndamentoTemPeriodoAberto() throws Exception {
        long antiga = finalizada("2025-03-01", "2025-03-05");
        long aberta = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-10",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));

        // Estender a antiga para depois do início da aberta cruza o período aberto.
        sobreposicao(moverDatas(antiga, "2025-03-01", "2025-03-10", 409));
        sobreposicao(moverDatas(antiga, "2025-03-01", "2025-04-30", 409));
        moverDatas(antiga, "2025-03-01", "2025-03-09", 200);

        // Mover o início da aberta para dentro da antiga também é recusado.
        sobreposicao(chamar(PUT(VIAGENS + "/" + aberta), token, corpo("dataInicio", "2025-03-09",
                "kmInicialCarregado", 0), 409));

        // Finalizar fecha o período sem conflito.
        finalizar(token, aberta, corpo("dataFim", "2025-03-12", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
    }

    @Test
    void odometroPodeRecomecarEntreFretesMasNaoDentroDoFrete() throws Exception {
        finalizada("2025-03-01", "2025-03-02");   // km 0 → 400
        // Próximo frete recomeça os contadores: o km 0 de novo é aceito.
        long segundo = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-03",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialVazio", 0, "kmInicialCarregado", 50));
        JsonNode r = finalizar(token, segundo, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 350,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        assertEquals(50, r.get("kmVazio").asInt());
        assertEquals(300, r.get("kmCarregado").asInt());

        // Dentro do frete, as leituras continuam estritamente crescentes.
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "kmInicialVazio", 100, "kmInicialCarregado", 100), 400);
    }

    @Test
    void viagensDeOutroCaminhaoNaoConflitam() throws Exception {
        finalizada("2025-03-01", "2025-03-10");
        long outro = criarCaminhao(token);
        criarViagem(token, corpo("caminhaoId", outro, "dataInicio", "2025-03-05", "kmInicialVazio", 0));
    }
}
