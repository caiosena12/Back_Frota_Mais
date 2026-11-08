package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * P19: o deslocamento vazio e seus custos pertencem ao PRÓXIMO frete.
 * O frete anterior é finalizado na descarga; a nova viagem começa na saída
 * vazia (mesmo dia, se for o caso) e recebe os lançamentos desse trecho.
 */
class DeslocamentoVazioTest extends ApiViagemTeste {

    @Test
    void proximoFreteComecaNoDiaDaDescargaERecebeOsCustosDoVazio() throws Exception {
        String token = registrar().token();
        long caminhaoId = criarCaminhao(token);

        // Frete anterior: descarga em 10/03, finalizado nesse momento.
        long anterior = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-08",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, anterior, "2025-03-08", "100", "600.00");
        JsonNode fotografiaAnterior = finalizar(token, anterior, corpo("dataFim", "2025-03-10",
                "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");

        // Saída vazia no mesmo dia da descarga: abre o próximo frete, só com o km da saída.
        long proximo = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-10",
                "kmInicialVazio", 0));
        // Não pode haver duas viagens abertas.
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-11",
                "kmInicialVazio", 0), 409);

        // Custos do trecho vazio (passando por várias cidades) vão para o próximo frete.
        pedagio(token, proximo, "2025-03-10", "25.00");
        diesel(token, proximo, "2025-03-11", "100", "650.00");
        arla(token, proximo, "2025-03-11", "10", "30.00");
        pedagio(token, proximo, "2025-03-11", "15.00");
        despesaExtra(token, proximo, "Estacionamento", "2025-03-11", "20.00");
        // O frete anterior, finalizado, não recebe lançamentos novos.
        chamar(POST(VIAGENS + "/" + anterior + "/pedagios"), token, corpo("data", "2025-03-10", "valor", "1.00"), 409);

        // Carregamento: km vazio acumulado até aqui; começa o percurso carregado.
        chamar(PUT(VIAGENS + "/" + proximo), token, corpo("dataInicio", "2025-03-10", "origem", "C",
                "destino", "D", "valorFrete", "3000.00", "kmInicialVazio", 0, "kmInicialCarregado", 300), 200);
        JsonNode r = finalizar(token, proximo, corpo("dataFim", "2025-03-13", "kmFinalCarregado", 800,
                "consumoMedioVazio", "3.00", "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");

        assertEquals(300, r.get("kmVazio").asInt());
        assertEquals(500, r.get("kmCarregado").asInt());
        dinheiro("650.00", r.get("custoDieselVazio"));        // 300 ÷ 3 = 100 L × 6,50
        dinheiro("1300.00", r.get("custoDieselCarregado"));   // 500 ÷ 2,5 = 200 L × 6,50
        dinheiro("40.00", r.get("pedagios"));
        dinheiro("30.00", r.get("custoArla"));
        dinheiro("20.00", r.get("despesasExtras"));
        assertTrue(r.get("calculoCompleto").asBoolean());

        // O frete anterior continua exatamente como foi finalizado.
        assertEquals(fotografiaAnterior, buscarViagem(token, anterior).get("rentabilidade"));
        dinheiro("0.00", fotografiaAnterior.get("pedagios"));
    }
}
