package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.Datas;
import tools.jackson.databind.JsonNode;

/**
 * Situações do caminhão (ATIVO, INATIVO, EXCLUIDO lógico) e preservação do
 * histórico: com viagem aberta o caminhão não é inativado nem excluído; a
 * exclusão é lógica e nada do histórico se perde.
 */
class CaminhaoHistoricoTest extends ApiViagemTeste {

    private String token;
    private long caminhaoId;
    private String placa;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
        placa = chamar(GET("/api/caminhoes/" + caminhaoId), token, null, 200).get("placa").asString();
    }

    private Map<String, Object> caminhao(String status) {
        return corpo("placa", placa, "marca", "Scania", "modelo", "Caminhao de teste", "ano", 2022, "eixos", 6,
                "status", status, "capacidadeTanqueLitros", 600);
    }

    private long viagemAberta(String inicio) throws Exception {
        return criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
    }

    private Map<String, Object> historica(String inicio, String fim) {
        return corpo("caminhaoId", caminhaoId, "dataInicio", inicio, "dataFim", fim,
                "origem", "A", "destino", "B", "valorFrete", "1000.00",
                "kmInicialCarregado", 0, "kmFinalCarregado", 500, "consumoMedioCarregado", "2.50");
    }

    private JsonNode caminhaoAtual() throws Exception {
        return chamar(GET("/api/caminhoes/" + caminhaoId), token, null, 200);
    }

    private boolean listado(String url) throws Exception {
        for (JsonNode c : chamar(GET(url), token, null, 200)) {
            if (c.get("id").asLong() == caminhaoId) {
                return true;
            }
        }
        return false;
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void caminhaoComViagemEmAndamentoNaoEInativado() throws Exception {
        long id = viagemAberta("2025-03-01");
        erro(chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 409), "viagem em andamento");
        assertEquals("ATIVO", caminhaoAtual().get("status").asString());

        // Outras alterações do caminhão continuam permitidas.
        chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("ATIVO"), 200);

        // Depois de finalizar, pode inativar; e caminhão inativo não abre viagem.
        finalizar(token, id, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 200);
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "kmInicialVazio", 0), 409), "inativo");
    }

    @Test
    void inativacaoRegistraOMomentoEReativacaoOLimpa() throws Exception {
        JsonNode inativo = chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 200);
        assertFalse(inativo.get("inativadoEm").isNull(), inativo::toString);

        // Repetir INATIVO não muda a data da inativação.
        JsonNode denovo = chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 200);
        assertEquals(inativo.get("inativadoEm").asString(), denovo.get("inativadoEm").asString());

        JsonNode ativo = chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("ATIVO"), 200);
        assertTrue(ativo.get("inativadoEm").isNull(), ativo::toString);
    }

    @Test
    void exclusaoLogicaPreservaCaminhaoViagensEResultado() throws Exception {
        long aberta = viagemAberta("2025-03-01");
        erro(chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 409), "viagem em andamento");

        finalizar(token, aberta, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode antes = buscarViagem(token, aberta);
        chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 204);

        // O caminhão continua consultável pelo dono, marcado como excluído.
        JsonNode excluido = caminhaoAtual();
        assertEquals("EXCLUIDO", excluido.get("status").asString());
        assertFalse(excluido.get("excluidoEm").isNull(), excluido::toString);

        // Sai da lista operacional, mas aparece quando o histórico é pedido.
        assertFalse(listado("/api/caminhoes"));
        assertTrue(listado("/api/caminhoes?incluirExcluidos=true"));

        // Viagem e fotografia financeira intactas.
        JsonNode depois = buscarViagem(token, aberta);
        assertEquals("FINALIZADA", depois.get("status").asString());
        assertEquals(antes.get("rentabilidade"), depois.get("rentabilidade"));

        // Não entra em nova viagem, não é alterado, e não é excluído de novo.
        erro(chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "kmInicialVazio", 0), 409), "excluído");
        erro(chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("ATIVO"), 409), "excluído");
        erro(chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 409), "já está excluído");
    }

    @Test
    void caminhaoSemViagensTambemTemExclusaoLogica() throws Exception {
        chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 204);
        assertEquals("EXCLUIDO", caminhaoAtual().get("status").asString());
    }

    @Test
    void placaDeCaminhaoExcluidoPodeSerCadastradaDeNovo() throws Exception {
        chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 204);
        JsonNode novo = chamar(POST("/api/caminhoes"), token, caminhao("ATIVO"), 201);
        assertEquals(placa, novo.get("placa").asString());
        // O excluído continua com a placa no histórico.
        assertEquals(placa, caminhaoAtual().get("placa").asString());
    }

    @Test
    void placaENormalizadaEDuplicataComSeparadorERecusada() throws Exception {
        JsonNode criado = chamar(POST("/api/caminhoes"), token, corpo("placa", " abc-1d23 ", "marca", "Volvo",
                "modelo", "FH", "ano", 2021, "eixos", 3), 201);
        assertEquals("ABC1D23", criado.get("placa").asString());
        erro(chamar(POST("/api/caminhoes"), token, corpo("placa", "ABC 1D23", "marca", "Volvo",
                "modelo", "FH", "ano", 2021, "eixos", 3), 409), "placa");
    }

    @Test
    void alteracaoSemStatusERecusadaParaNaoReativarPorEngano() throws Exception {
        chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 200);
        Map<String, Object> semStatus = caminhao("INATIVO");
        semStatus.remove("status");
        erro(chamar(PUT("/api/caminhoes/" + caminhaoId), token, semStatus, 400), "status");
        assertEquals("INATIVO", caminhaoAtual().get("status").asString());
    }

    @Test
    void statusExcluidoSoPelaExclusao() throws Exception {
        erro(chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("EXCLUIDO"), 400), "DELETE");
    }

    @Test
    void viagemHistoricaEmCaminhaoInativoSoAntesDaInativacao() throws Exception {
        chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 200);
        String hoje = Datas.hoje().toString();
        String ontem = Datas.hoje().minusDays(1).toString();

        // Terminou antes da inativação (hoje): aceita.
        chamar(POST(VIAGENS + "/historicas"), token, historica("2025-02-01", "2025-02-03"), 201);
        chamar(POST(VIAGENS + "/historicas"), token, historica(Datas.hoje().minusDays(3).toString(), ontem), 201);

        // Terminou no dia da inativação ou depois: recusa.
        erro(chamar(POST(VIAGENS + "/historicas"), token, historica(hoje, hoje), 409), "antes da inativação");
    }

    @Test
    void viagemHistoricaEmCaminhaoExcluidoERecusada() throws Exception {
        chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 204);
        erro(chamar(POST(VIAGENS + "/historicas"), token, historica("2025-02-01", "2025-02-03"), 409), "excluído");
    }

    @Test
    void outraEmpresaNaoInativaNemExcluiCaminhao() throws Exception {
        String outro = registrar().token();
        chamar(PUT("/api/caminhoes/" + caminhaoId), outro, caminhao("INATIVO"), 404);
        chamar(DELETE("/api/caminhoes/" + caminhaoId), outro, null, 404);
        assertEquals("ATIVO", caminhaoAtual().get("status").asString());
    }
}
