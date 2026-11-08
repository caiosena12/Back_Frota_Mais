package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/** Caminhão com viagem aberta não é inativado; com histórico de viagens, não é excluído. */
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
        return corpo("placa", placa, "modelo", "Caminhao de teste", "ano", 2022, "status", status,
                "capacidadeTanqueLitros", 600);
    }

    private long viagemAberta(String inicio) throws Exception {
        return criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void caminhaoComViagemEmAndamentoNaoEInativado() throws Exception {
        long id = viagemAberta("2025-03-01");
        erro(chamar(PUT("/api/caminhoes/" + caminhaoId), token, caminhao("INATIVO"), 409), "viagem em andamento");
        assertEquals("ATIVO", chamar(GET("/api/caminhoes/" + caminhaoId), token, null, 200).get("status").asString());

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
    void caminhaoComHistoricoNaoEExcluido() throws Exception {
        long aberta = viagemAberta("2025-03-01");
        erro(chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 409), "histórico de viagens");

        finalizar(token, aberta, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        erro(chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 409), "histórico de viagens");

        // Caminhão e histórico preservados.
        chamar(GET("/api/caminhoes/" + caminhaoId), token, null, 200);
        assertEquals("FINALIZADA", buscarViagem(token, aberta).get("status").asString());
    }

    @Test
    void caminhaoSemViagensContinuaExcluivel() throws Exception {
        chamar(DELETE("/api/caminhoes/" + caminhaoId), token, null, 204);
        chamar(GET("/api/caminhoes/" + caminhaoId), token, null, 404);
    }

    @Test
    void outraEmpresaNaoInativaNemExcluiCaminhao() throws Exception {
        String outro = registrar().token();
        chamar(PUT("/api/caminhoes/" + caminhaoId), outro, caminhao("INATIVO"), 404);
        chamar(DELETE("/api/caminhoes/" + caminhaoId), outro, null, 404);
    }
}
