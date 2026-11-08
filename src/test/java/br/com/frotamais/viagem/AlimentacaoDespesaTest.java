package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Alimentação não é despesa extra do frete, em nenhuma grafia. */
class AlimentacaoDespesaTest extends ApiViagemTeste {

    @Test
    void reconheceVariacoesDoTermo() {
        for (String d : new String[] {"Alimentação", "alimentacao", "ALIMENTAÇÃO", "  AlImEnTaÇãO  ",
                "Alimentação do motorista", "despesa com alimentação", "Gasto\tde   ALIMENTACAO"}) {
            assertTrue(LancamentoService.ehAlimentacao(d), d);
        }
        for (String d : new String[] {"Lavagem", "Borracharia", "Chapa para descarga", "Estacionamento"}) {
            assertFalse(LancamentoService.ehAlimentacao(d), d);
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
}
