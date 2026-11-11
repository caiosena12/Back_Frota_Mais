package br.com.frotamais.manutencao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.Datas;
import br.com.frotamais.viagem.ApiViagemTeste;
import tools.jackson.databind.JsonNode;

/**
 * Manutenções: só DONO, isoladas por empresa, ligadas ao caminhão e não a um
 * frete. Exclusão lógica; caminhão excluído congela o histórico.
 */
class ManutencaoTest extends ApiViagemTeste {

    private static final String MANUTENCOES = "/api/manutencoes";

    private String dono;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
    }

    private JsonNode criar(String token, long caminhao, String data, String descricao, String valor, int status)
            throws Exception {
        return chamar(POST(MANUTENCOES), token, corpo("caminhaoId", caminhao, "data", data,
                "descricao", descricao, "valor", valor), status);
    }

    private long criar(long caminhao, String data, String valor) throws Exception {
        return criar(dono, caminhao, data, "Troca de óleo", valor, 201).get("id").asLong();
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void cadastraConsultaAlteraEExcluiLogicamente() throws Exception {
        JsonNode criada = criar(dono, caminhaoId, "2025-06-10", "  Troca de pneus dianteiros  ", "1800.50", 201);
        long id = criada.get("id").asLong();
        assertEquals("Troca de pneus dianteiros", criada.get("descricao").asString());
        assertEquals(caminhaoId, criada.get("caminhaoId").asLong());
        assertFalse(criada.get("placa").isNull());
        dinheiro("1800.50", criada.get("valor"));

        JsonNode alterada = chamar(PUT(MANUTENCOES + "/" + id), dono, corpo("caminhaoId", caminhaoId,
                "data", "2025-06-11", "descricao", "Troca de pneus", "valor", "1900.00"), 200);
        assertEquals("2025-06-11", alterada.get("data").asString());
        dinheiro("1900.00", chamar(GET(MANUTENCOES + "/" + id), dono, null, 200).get("valor"));

        chamar(DELETE(MANUTENCOES + "/" + id), dono, null, 204);
        assertEquals(0, chamar(GET(MANUTENCOES), dono, null, 200).size());
        JsonNode comExcluidas = chamar(GET(MANUTENCOES + "?incluirExcluidas=true"), dono, null, 200);
        assertEquals(1, comExcluidas.size());
        assertFalse(comExcluidas.get(0).get("excluidaEm").isNull());
        assertFalse(chamar(GET(MANUTENCOES + "/" + id), dono, null, 200).get("excluidaEm").isNull());

        erro(chamar(DELETE(MANUTENCOES + "/" + id), dono, null, 409), "já foi excluída");
        erro(chamar(PUT(MANUTENCOES + "/" + id), dono, corpo("caminhaoId", caminhaoId, "data", "2025-06-11",
                "descricao", "x", "valor", "1.00"), 409), "já foi excluída");
    }

    @Test
    void filtraPorCaminhaoEPeriodo() throws Exception {
        long outro = criarCaminhao(dono);
        criar(caminhaoId, "2025-05-31", "100.00");
        criar(caminhaoId, "2025-06-15", "200.00");
        criar(outro, "2025-06-20", "300.00");

        assertEquals(3, chamar(GET(MANUTENCOES), dono, null, 200).size());
        assertEquals(2, chamar(GET(MANUTENCOES + "?de=2025-06-01&ate=2025-06-30"), dono, null, 200).size());
        JsonNode doCaminhao = chamar(GET(MANUTENCOES + "?caminhaoId=" + caminhaoId + "&de=2025-06-01"),
                dono, null, 200);
        assertEquals(1, doCaminhao.size());
        dinheiro("200.00", doCaminhao.get(0).get("valor"));
        chamar(GET(MANUTENCOES + "?de=2025-06-30&ate=2025-06-01"), dono, null, 400);
    }

    @Test
    void validaDadosSemRestringirADescricao() throws Exception {
        erro(criar(dono, caminhaoId, Datas.hoje().plusDays(1).toString(), "x", "10.00", 400), "futuro");
        criar(dono, caminhaoId, "2025-06-10", "x", "0", 400);
        criar(dono, caminhaoId, "2025-06-10", "x", "-5.00", 400);
        criar(dono, caminhaoId, "2025-06-10", "   ", "10.00", 400);
        criar(dono, caminhaoId, "2025-06-10", "x".repeat(501), "10.00", 400);
        // Descrição livre: nada é bloqueado por palavra.
        criar(dono, caminhaoId, "2025-06-10", "Alimentação do mecânico na estrada", "35.00", 201);
        assertEquals(1, chamar(GET(MANUTENCOES), dono, null, 200).size());
    }

    @Test
    void caminhaoInativoAceitaEExcluidoCongelaOHistorico() throws Exception {
        String placa = chamar(GET("/api/caminhoes/" + caminhaoId), dono, null, 200).get("placa").asString();
        chamar(PUT("/api/caminhoes/" + caminhaoId), dono, corpo("placa", placa, "marca", "Scania",
                "modelo", "R450", "ano", 2022, "eixos", 6, "status", "INATIVO"), 200);
        long id = criar(caminhaoId, "2025-06-10", "500.00");

        chamar(DELETE("/api/caminhoes/" + caminhaoId), dono, null, 204);
        erro(criar(dono, caminhaoId, "2025-06-11", "x", "10.00", 409), "excluído");
        erro(chamar(PUT(MANUTENCOES + "/" + id), dono, corpo("caminhaoId", caminhaoId, "data", "2025-06-10",
                "descricao", "x", "valor", "1.00"), 409), "excluído");
        erro(chamar(DELETE(MANUTENCOES + "/" + id), dono, null, 409), "excluído");
        // Continua no histórico.
        assertEquals(1, chamar(GET(MANUTENCOES + "?caminhaoId=" + caminhaoId), dono, null, 200).size());

        // Nem pode ser movida para um caminhão excluído.
        long ativo = criarCaminhao(dono);
        long outra = criar(ativo, "2025-06-10", "50.00");
        chamar(PUT(MANUTENCOES + "/" + outra), dono, corpo("caminhaoId", caminhaoId, "data", "2025-06-10",
                "descricao", "x", "valor", "50.00"), 409);
    }

    @Test
    void isolamentoEntreEmpresas() throws Exception {
        long id = criar(caminhaoId, "2025-06-10", "500.00");
        String outro = registrar().token();
        long caminhaoDoOutro = criarCaminhao(outro);

        assertEquals(0, chamar(GET(MANUTENCOES), outro, null, 200).size());
        chamar(GET(MANUTENCOES + "/" + id), outro, null, 404);
        chamar(PUT(MANUTENCOES + "/" + id), outro, corpo("caminhaoId", caminhaoDoOutro, "data", "2025-06-10",
                "descricao", "x", "valor", "1.00"), 404);
        chamar(DELETE(MANUTENCOES + "/" + id), outro, null, 404);
        // Não lança no caminhão de outra empresa, nem move para ele.
        criar(outro, caminhaoId, "2025-06-10", "x", "1.00", 404);
        chamar(PUT(MANUTENCOES + "/" + id), dono, corpo("caminhaoId", caminhaoDoOutro, "data", "2025-06-10",
                "descricao", "x", "valor", "1.00"), 404);

        JsonNode intacta = chamar(GET(MANUTENCOES + "/" + id), dono, null, 200);
        dinheiro("500.00", intacta.get("valor"));
        assertTrue(intacta.get("excluidaEm").isNull());
    }

    @Test
    void motoristaESemTokenNaoAcessam() throws Exception {
        long id = criar(caminhaoId, "2025-06-10", "500.00");
        String motorista = criarMotorista(dono, caminhaoId).token();
        chamar(GET(MANUTENCOES), motorista, null, 403);
        chamar(GET(MANUTENCOES + "/" + id), motorista, null, 403);
        criar(motorista, caminhaoId, "2025-06-10", "x", "1.00", 403);
        chamar(PUT(MANUTENCOES + "/" + id), motorista, corpo("caminhaoId", caminhaoId, "data", "2025-06-10",
                "descricao", "x", "valor", "1.00"), 403);
        chamar(DELETE(MANUTENCOES + "/" + id), motorista, null, 403);
        chamar(GET(MANUTENCOES), null, null, 401);
        assertEquals(1, chamar(GET(MANUTENCOES), dono, null, 200).size());
    }

    @Test
    void naoAlteraARentabilidadeDasViagens() throws Exception {
        long viagem = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        diesel(dono, viagem, "2025-06-01", "100", "600.00");
        finalizar(dono, viagem, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        String antes = buscarViagem(dono, viagem).get("rentabilidade").toString();

        criar(caminhaoId, "2025-06-02", "5000.00");
        assertEquals(antes, buscarViagem(dono, viagem).get("rentabilidade").toString());
    }
}
