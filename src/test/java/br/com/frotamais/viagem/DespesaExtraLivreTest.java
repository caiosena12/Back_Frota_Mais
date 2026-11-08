package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * P17: despesas extras são livres. Cada empresa decide o que registrar; não há
 * bloqueio por palavra nem categoria. Valem só as validações dos campos
 * (descrição, data e valor), e cada despesa entra uma única vez no custo.
 */
class DespesaExtraLivreTest extends ApiViagemTeste {

    private static final String[] DESCRICOES = {"Alimentação", "alimentacao", "ALIMENTAÇÃO do motorista",
            "Refeição", "Lanche", "Almoço", "Jantar", "Café da manhã", "Café", "Restaurante Beira Rio",
            "Marmita", "Comida", "Lavagem do caminhão", "Chapa para descarga", "Estacionamento"};

    @Test
    void aceitaQualquerDescricaoNaInclusaoENaCorrecaoESomaUmaVezNoCusto() throws Exception {
        String token = registrar().token();
        long caminhaoId = criarCaminhao(token);
        long viagem = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        diesel(token, viagem, "2025-03-01", "100", "600.00");

        BigDecimal soma = BigDecimal.ZERO;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < DESCRICOES.length; i++) {
            BigDecimal valor = new BigDecimal(10 + i).setScale(2);
            JsonNode d = despesaExtra(token, viagem, DESCRICOES[i], "2025-03-01", valor.toPlainString());
            assertEquals(DESCRICOES[i].trim(), d.get("descricao").asString());
            ids.add(d.get("id").asLong());
            soma = soma.add(valor);
        }

        // Correção para outra descrição de alimentação também é aceita, sem mudar o valor.
        String url = VIAGENS + "/" + viagem + "/despesas-extras/";
        chamar(PUT(url + ids.get(0)), token, corpo("descricao", "Almoço e jantar", "data", "2025-03-01",
                "valor", "10.00"), 200);

        JsonNode v = buscarViagem(token, viagem);
        assertEquals(DESCRICOES.length, v.get("despesasExtras").size());
        dinheiro(soma.toPlainString(), v.get("rentabilidade").get("despesasExtras"));   // 10 + 11 + ... + 24 = 255

        // Na finalização, cada despesa entra uma única vez no custo operacional:
        // diesel 400 km ÷ 2,5 = 160 L × 6,00 = 960; + extras 255 = 1.215.
        JsonNode r = finalizar(token, viagem, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        dinheiro("255.00", r.get("despesasExtras"));
        dinheiro("1215.00", r.get("custoOperacionalTotal"));
        dinheiro("785.00", r.get("lucro"));
        assertEquals(0, r.get("pendencias").size());
    }

    @Test
    void cadastroHistoricoAceitaQualquerDescricao() throws Exception {
        String token = registrar().token();
        List<Map<String, Object>> extras = new ArrayList<>();
        for (String d : new String[] {"Almoço", "Café da manhã", "Marmita", "Restaurante"}) {
            extras.add(corpo("descricao", d, "data", "2025-03-10", "valor", "25.00"));
        }
        JsonNode v = chamar(POST(VIAGENS + "/historicas"), token, corpo("caminhaoId", criarCaminhao(token),
                "dataInicio", "2025-03-10", "dataFim", "2025-03-12", "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 0, "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50", "despesasExtras", extras), 201);
        assertEquals(4, v.get("despesasExtras").size());
        dinheiro("100.00", v.get("rentabilidade").get("despesasExtras"));
    }

    @Test
    void continuaValidandoOsCampos() throws Exception {
        String token = registrar().token();
        long viagem = criarViagem(token, corpo("caminhaoId", criarCaminhao(token), "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        String url = VIAGENS + "/" + viagem + "/despesas-extras";

        chamar(POST(url), token, corpo("descricao", "", "data", "2025-03-01", "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", "   ", "data", "2025-03-01", "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", null, "data", "2025-03-01", "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", "x".repeat(201), "data", "2025-03-01", "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", "Almoço", "data", null, "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", "Almoço", "data", "2025-02-28", "valor", "10.00"), 400);
        chamar(POST(url), token, corpo("descricao", "Almoço", "data", "2025-03-01", "valor", "0"), 400);
        chamar(POST(url), token, corpo("descricao", "Almoço", "data", "2025-03-01", "valor", null), 400);

        assertEquals(0, buscarViagem(token, viagem).get("despesasExtras").size());
    }
}
