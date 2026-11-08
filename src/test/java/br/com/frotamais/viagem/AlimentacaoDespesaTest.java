package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Alimentação não é despesa extra do frete, em nenhuma grafia (P17): o termo
 * "alimentação" e palavras que a identificam (refeição, lanche, almoço,
 * jantar, café, restaurante, marmita, comida), reconhecidas por palavra
 * inteira, sem acento, caixa ou espaços repetidos.
 */
class AlimentacaoDespesaTest extends ApiViagemTeste {

    @Test
    void reconheceVariacoesDoTermo() {
        for (String d : new String[] {"Alimentação", "alimentacao", "ALIMENTAÇÃO", "  AlImEnTaÇãO  ",
                "Alimentação do motorista", "despesa com alimentação", "Gasto\tde   ALIMENTACAO"}) {
            assertTrue(RegrasLancamento.ehAlimentacao(d), d);
        }
        for (String d : new String[] {"Lavagem", "Borracharia", "Chapa para descarga", "Estacionamento"}) {
            assertFalse(RegrasLancamento.ehAlimentacao(d), d);
        }
    }

    @Test
    void apiRecusaAlimentacaoNaInclusaoENaCorrecao() throws Exception {
        String token = registrar().token();
        long viagem = criarViagem(token, corpo("caminhaoId", criarCaminhao(token), "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        String url = VIAGENS + "/" + viagem + "/despesas-extras";

        for (String d : new String[] {"Alimentação", "alimentacao", "ALIMENTAÇÃO", "Alimentação do motorista"}) {
            var erro = chamar(POST(url), token, corpo("descricao", d, "data", "2025-03-01", "valor", "45.00"), 400);
            assertTrue(erro.get("detalhes").toString().contains("Alimentação não é despesa"), erro::toString);
        }

        long extra = despesaExtra(token, viagem, "Lavagem", "2025-03-01", "50.00").get("id").asLong();
        chamar(PUT(url + "/" + extra), token, corpo("descricao", "alimentação", "data", "2025-03-01",
                "valor", "45.00"), 400);

        var v = buscarViagem(token, viagem);
        assertEquals(1, v.get("despesasExtras").size());
        assertEquals("Lavagem", v.get("despesasExtras").get(0).get("descricao").asString());
        dinheiro("50.00", v.get("rentabilidade").get("despesasExtras"));
    }

    @Test
    void reconhecePalavrasDeAlimentacao() {
        for (String d : new String[] {"Refeição", "REFEICOES", "refeicao na estrada", "Lanche", "lanches",
                "Almoço", "almoco do motorista", "ALMOÇOS", "Jantar", "janTAR em Registro", "Café",
                "cafe", "Café da manhã", "  CAFÉ   DA   MANHÃ  ", "café-da-manhã", "Restaurante Beira Rio",
                "restaurantes", "Marmita", "marmitas", "Comida", "Comida/bebida", "Despesa: almoço.",
                "Gasto com\tREFEIÇÃO"}) {
            assertTrue(RegrasLancamento.ehAlimentacao(d), d);
        }
    }

    @Test
    void naoBloqueiaPorCorrespondenciaParcial() {
        for (String d : new String[] {"Almoxarifado", "Comodato de paletes", "Lavagem do caminhão",
                "Lona e cordas", "Chapa para descarga", "Taxa de balança", "Cafeicultura", "Jantes de roda",
                "Lanchas", "Restauração do baú", "Marmitaria fechada", "Refeitorio"}) {
            assertFalse(RegrasLancamento.ehAlimentacao(d), d);
        }
    }

    @Test
    void apiRecusaPalavrasDeAlimentacaoEAceitaDescricoesValidas() throws Exception {
        String token = registrar().token();
        long viagem = criarViagem(token, corpo("caminhaoId", criarCaminhao(token), "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        String url = VIAGENS + "/" + viagem + "/despesas-extras";

        for (String d : new String[] {"Almoço", "Café da manhã", "Restaurante", "MARMITA", "Lanche", "Jantar",
                "Refeição", "comida"}) {
            var erro = chamar(POST(url), token, corpo("descricao", d, "data", "2025-03-01", "valor", "30.00"), 400);
            assertTrue(erro.get("detalhes").toString().contains("Alimentação não é despesa"), erro::toString);
        }
        despesaExtra(token, viagem, "Almoxarifado", "2025-03-01", "20.00");
        despesaExtra(token, viagem, "Chapa para descarga", "2025-03-01", "80.00");

        var v = buscarViagem(token, viagem);
        assertEquals(2, v.get("despesasExtras").size());
        dinheiro("100.00", v.get("rentabilidade").get("despesasExtras"));
    }
}
