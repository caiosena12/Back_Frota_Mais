package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Regressões A2 e A3 da auditoria: a referência de preço do diesel é congelada
 * na finalização. Nada fora do próprio frete altera seu resultado; só uma
 * correção explícita daquele frete recalcula a referência dele.
 */
class ReferenciaCongeladaTest extends ApiViagemTeste {

    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
    }

    /** Frete de R$ 1.000, km 0 → 400 a 2,5 km/L (160 L estimados). */
    private long abrir(String inicio) throws Exception {
        return criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
    }

    private JsonNode fechar(long id, String fim) throws Exception {
        return finalizar(token, id, corpo("dataFim", fim, "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
    }

    /** PUT de correção explícita do frete finalizado, sem mudar nenhum dado. */
    private JsonNode corrigir(long id, String inicio, String fim) throws Exception {
        return chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", inicio, "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 0, "consumoMedioCarregado", "2.50",
                "dataFim", fim, "kmFinalCarregado", 400), 200).get("rentabilidade");
    }

    private JsonNode rentabilidade(long id) throws Exception {
        return buscarViagem(token, id).get("rentabilidade");
    }

    @Test
    void cenarioA2_dieselRetroativoNaoAlteraFreteFinalizado() throws Exception {
        // X: frete anterior, finalizado só com Arla.
        long x = abrir("2025-03-01");
        long arlaX = arla(token, x, "2025-03-01", "100", "600.00").get("id").asLong();
        fechar(x, "2025-03-02");

        // A: finalizado sem nenhuma referência de diesel.
        long a = abrir("2025-03-10");
        JsonNode original = fechar(a, "2025-03-12");
        assertEquals("SEM_REFERENCIA", original.get("origemPrecoDiesel").asString());
        assertTrue(original.get("lucro").isNull());
        assertFalse(original.get("referenciaPrecoConsolidadaEm").isNull());

        // Cadastrar uma viagem retroativa com abastecimento é recusado (sobreposição, A1).
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "kmInicialCarregado", 0), 409);

        // Diesel aparece retroativamente em X (correção explícita de X: Arla → diesel).
        chamar(PUT(VIAGENS + "/" + x + "/abastecimentos/" + arlaX), token, corpo("tipo", "DIESEL",
                "data", "2025-03-01", "litros", "100", "valorTotal", "600.00"), 200);
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", rentabilidade(x).get("origemPrecoDiesel").asString());

        // A continua exatamente como foi finalizado.
        assertEquals(original, rentabilidade(a));

        // Correção explícita de A: só agora A passa a usar o preço de X.
        JsonNode corrigido = corrigir(a, "2025-03-10", "2025-03-12");
        assertEquals("ULTIMO_PRECO_DO_CAMINHAO", corrigido.get("origemPrecoDiesel").asString());
        assertEquals(x, corrigido.get("viagemReferenciaPrecoDieselId").asLong());
        dinheiro("6.00", corrigido.get("precoMedioDiesel"));
        dinheiro("40.00", corrigido.get("lucro"));   // 1000 − 160 L × 6,00
        assertNotEquals(original.get("referenciaPrecoConsolidadaEm"), corrigido.get("referenciaPrecoConsolidadaEm"));
    }

    @Test
    void cenarioA3_corrigirDieselAnteriorAfetaSoOFreteCorrigido() throws Exception {
        long f1 = abrir("2025-03-01");
        long dieselF1 = diesel(token, f1, "2025-03-01", "100", "600.00").get("id").asLong();
        fechar(f1, "2025-03-02");

        long f2 = abrir("2025-03-03");
        pedagio(token, f2, "2025-03-03", "0.01");
        long pedagioF2 = buscarViagem(token, f2).get("pedagios").get(0).get("id").asLong();
        JsonNode original = fechar(f2, "2025-03-04");
        assertEquals("ULTIMO_PRECO_DO_CAMINHAO", original.get("origemPrecoDiesel").asString());
        dinheiro("39.99", original.get("lucro"));     // 1000 − 960 − 0,01

        // Correção explícita do diesel de f1: f1 é recalculado...
        chamar(PUT(VIAGENS + "/" + f1 + "/abastecimentos/" + dieselF1), token, corpo("tipo", "DIESEL",
                "data", "2025-03-01", "litros", "100", "valorTotal", "700.00"), 200);
        JsonNode r1 = rentabilidade(f1);
        dinheiro("7.00", r1.get("precoMedioDiesel"));
        dinheiro("-120.00", r1.get("lucro"));

        // ...e f2, que usava o preço de f1, não muda.
        assertEquals(original, rentabilidade(f2));

        // Corrigir outro lançamento de f2 (pedágio) não reabre a referência de preço.
        chamar(PUT(VIAGENS + "/" + f2 + "/pedagios/" + pedagioF2), token,
                corpo("data", "2025-03-03", "valor", "0.02"), 200);
        JsonNode aposPedagio = rentabilidade(f2);
        dinheiro("6.00", aposPedagio.get("precoMedioDiesel"));
        dinheiro("39.98", aposPedagio.get("lucro"));
        assertEquals(original.get("referenciaPrecoConsolidadaEm"), aposPedagio.get("referenciaPrecoConsolidadaEm"));

        // Correção explícita de f2: agora sim usa o preço atual de f1.
        JsonNode corrigido = corrigir(f2, "2025-03-03", "2025-03-04");
        dinheiro("7.00", corrigido.get("precoMedioDiesel"));
        dinheiro("-120.02", corrigido.get("lucro"));
        // f1 não foi tocado pela correção de f2.
        assertEquals(r1, rentabilidade(f1));
    }

    @Test
    void viagemEmAndamentoNaoTemReferenciaCongelada() throws Exception {
        long f1 = abrir("2025-03-01");
        long dieselF1 = diesel(token, f1, "2025-03-01", "100", "600.00").get("id").asLong();
        fechar(f1, "2025-03-02");
        long aberta = abrir("2025-03-03");

        JsonNode r = rentabilidade(aberta);
        assertTrue(r.get("referenciaPrecoConsolidadaEm").isNull());
        dinheiro("6.00", r.get("precoMedioDiesel"));

        // Enquanto está em andamento, a viagem acompanha a correção do frete anterior.
        chamar(PUT(VIAGENS + "/" + f1 + "/abastecimentos/" + dieselF1), token, corpo("tipo", "DIESEL",
                "data", "2025-03-01", "litros", "100", "valorTotal", "700.00"), 200);
        dinheiro("7.00", rentabilidade(aberta).get("precoMedioDiesel"));

        // Ao finalizar, congela o preço daquele momento.
        JsonNode fin = fechar(aberta, "2025-03-04");
        dinheiro("7.00", fin.get("precoMedioDiesel"));
        assertFalse(fin.get("referenciaPrecoConsolidadaEm").isNull());
    }

    @Test
    void correcaoDoDieselDaPropriaViagemRecalculaElaMesma() throws Exception {
        long f = abrir("2025-03-01");
        long d1 = diesel(token, f, "2025-03-01", "100", "600.00").get("id").asLong();
        diesel(token, f, "2025-03-01", "200", "1260.00");
        dinheiro("6.20", fechar(f, "2025-03-02").get("precoMedioDiesel"));

        chamar(PUT(VIAGENS + "/" + f + "/abastecimentos/" + d1), token, corpo("tipo", "DIESEL",
                "data", "2025-03-01", "litros", "100", "valorTotal", "540.00"), 200);
        JsonNode r = rentabilidade(f);
        dinheiro("6.00", r.get("precoMedioDiesel"));   // (540 + 1260) ÷ 300
        dinheiro("40.00", r.get("lucro"));
    }
}
