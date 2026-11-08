package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;

/**
 * P18: a fotografia financeira gravada na finalização não muda quando os
 * limites globais mudam. Só uma correção explícita do frete aplica a política
 * atual, e só a ele.
 *
 * A mudança de configuração é simulada trocando o retorno de
 * PoliticaCalculo.limites(), o mesmo ponto que os serviços consultam a cada
 * operação. O EntityManager é esvaziado antes das consultas, para que o
 * resultado venha das colunas gravadas e não da memória.
 */
class FotografiaFinanceiraTest extends ApiViagemTeste {

    /** Diesel até R$ 5,00/L: o preço de R$ 6,00 usado nos fretes passa a ser implausível. */
    private static final LimitesPlausibilidade APERTADOS = new LimitesPlausibilidade(
            new BigDecimal("1.00"), new BigDecimal("5.00"), new BigDecimal("0.50"), new BigDecimal("10.00"));

    @MockitoSpyBean
    private PoliticaCalculo politica;

    @PersistenceContext
    private EntityManager em;

    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        token = registrar().token();
        caminhaoId = criarCaminhao(token);
    }

    /** Frete de R$ 1.000, 400 km carregado a 2,5 km/L com diesel próprio a R$ 6,00/L: lucro 40. */
    private long freteFinalizado(String inicio, String fim) throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        diesel(token, id, inicio, "100", "600.00");
        finalizar(token, id, corpo("dataFim", fim, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200);
        return id;
    }

    private JsonNode rentabilidade(long id) throws Exception {
        em.flush();
        em.clear();
        return buscarViagem(token, id).get("rentabilidade");
    }

    private JsonNode corrigir(long id, String inicio, String fim) throws Exception {
        return chamar(PUT(VIAGENS + "/" + id), token, corpo("dataInicio", inicio, "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 0, "consumoMedioCarregado", "2.50",
                "dataFim", fim, "kmFinalCarregado", 400), 200).get("rentabilidade");
    }

    @Test
    void fotografiaGuardaResultadoEPoliticaDaFinalizacao() throws Exception {
        long a = freteFinalizado("2025-03-01", "2025-03-02");
        JsonNode r = rentabilidade(a);

        assertFalse(r.get("resultadoConsolidadoEm").isNull());
        assertTrue(r.get("calculoCompleto").asBoolean());
        assertEquals(0, r.get("pendencias").size());
        dinheiro("960.00", r.get("custoDieselCarregado"));
        dinheiro("960.00", r.get("custoOperacionalTotal"));
        dinheiro("40.00", r.get("lucro"));
        dinheiro("4.00", r.get("margemPercentual"));
        JsonNode p = r.get("politicaCalculo");
        assertEquals(CalculadoraRentabilidade.VERSAO_POLITICA, p.get("versao").asString());
        dinheiro("1.00", p.get("precoDieselMinimo"));
        dinheiro("30.00", p.get("precoDieselMaximo"));
        dinheiro("0.50", p.get("consumoMinimo"));
        dinheiro("10.00", p.get("consumoMaximo"));
    }

    @Test
    void mudarLimitesNaoAlteraFretesFinalizados() throws Exception {
        long a = freteFinalizado("2025-03-01", "2025-03-02");
        long b = freteFinalizado("2025-03-03", "2025-03-04");
        JsonNode originalA = rentabilidade(a);
        JsonNode originalB = rentabilidade(b);
        JsonNode indicadoresAntes = chamar(GET(VIAGENS + "/indicadores?ano=2025"), token, null, 200);

        doReturn(APERTADOS).when(politica).limites();

        // Fretes finalizados: exatamente o resultado gravado, com a política antiga.
        assertEquals(originalA, rentabilidade(a));
        assertEquals(originalB, rentabilidade(b));
        assertEquals(indicadoresAntes, chamar(GET(VIAGENS + "/indicadores?ano=2025"), token, null, 200));
        for (JsonNode linha : chamar(GET(VIAGENS + "?status=FINALIZADA"), token, null, 200)) {
            assertTrue(linha.get("calculoCompleto").asBoolean());
            dinheiro("40.00", linha.get("lucro"));
        }

        // Viagem em andamento usa a política atual: o mesmo preço agora é implausível e aparece como pendência.
        long aberta = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-05",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0,
                "consumoMedioCarregado", "2.50"));
        JsonNode viva = rentabilidade(aberta);
        assertTrue(viva.get("resultadoConsolidadoEm").isNull());
        dinheiro("5.00", viva.get("politicaCalculo").get("precoDieselMaximo"));
    }

    @Test
    void correcaoExplicitaRecalculaSoOFreteCorrigidoComAPoliticaAtual() throws Exception {
        long a = freteFinalizado("2025-03-01", "2025-03-02");
        long b = freteFinalizado("2025-03-03", "2025-03-04");
        JsonNode originalA = rentabilidade(a);
        JsonNode originalB = rentabilidade(b);

        doReturn(APERTADOS).when(politica).limites();
        corrigir(a, "2025-03-01", "2025-03-02");

        // A: recalculado com a política atual. O preço de R$ 6,00 ficou fora do limite: cálculo
        // incompleto, com o motivo exposto, e nenhum custo vira zero.
        JsonNode novoA = rentabilidade(a);
        assertNotEquals(originalA.get("resultadoConsolidadoEm"), novoA.get("resultadoConsolidadoEm"));
        dinheiro("5.00", novoA.get("politicaCalculo").get("precoDieselMaximo"));
        assertFalse(novoA.get("calculoCompleto").asBoolean());
        assertTrue(novoA.get("custoDieselCarregado").isNull());
        assertTrue(novoA.get("lucro").isNull());
        assertTrue(novoA.get("pendencias").toString().contains("fora do limite"), novoA::toString);

        // B: intocado.
        assertEquals(originalB, rentabilidade(b));

        // Nos indicadores, A sai dos totais oficiais e aparece como incompleto.
        JsonNode ind = chamar(GET(VIAGENS + "/indicadores?ano=2025"), token, null, 200);
        assertEquals(1, ind.get("viagensConsideradas").asInt());
        assertEquals(1, ind.get("viagensIncompletas").asInt());
        assertEquals(a, ind.get("viagensComCalculoIncompleto").get(0).asLong());
        dinheiro("1000.00", ind.get("receitaTotal"));
        dinheiro("1000.00", ind.get("valorFreteIncompletasNaoIncluido"));
    }

    @Test
    void correcaoDePedagioRegravaSoAFotografiaDoFreteMantendoOPrecoCongelado() throws Exception {
        long a = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        diesel(token, a, "2025-03-01", "100", "600.00");
        long pedagioA = pedagio(token, a, "2025-03-01", "10.00").get("id").asLong();
        finalizar(token, a, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
        long b = freteFinalizado("2025-03-03", "2025-03-04");
        JsonNode originalA = rentabilidade(a);
        JsonNode originalB = rentabilidade(b);
        dinheiro("30.00", originalA.get("lucro"));

        doReturn(APERTADOS).when(politica).limites();
        chamar(PUT(VIAGENS + "/" + a + "/pedagios/" + pedagioA), token,
                corpo("data", "2025-03-01", "valor", "20.00"), 200);

        JsonNode novoA = rentabilidade(a);
        // Mesma referência de preço (não foi reavaliada)...
        assertEquals(originalA.get("referenciaPrecoConsolidadaEm"), novoA.get("referenciaPrecoConsolidadaEm"));
        dinheiro("6.00", novoA.get("precoMedioDiesel"));
        // ...mas a fotografia foi regravada com a política atual, que considera esse preço implausível.
        assertNotEquals(originalA.get("resultadoConsolidadoEm"), novoA.get("resultadoConsolidadoEm"));
        dinheiro("20.00", novoA.get("pedagios"));
        assertFalse(novoA.get("calculoCompleto").asBoolean());
        assertTrue(novoA.get("lucro").isNull());

        assertEquals(originalB, rentabilidade(b));
    }
}
