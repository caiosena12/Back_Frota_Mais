package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.JsonNode;

/**
 * P16: cadastro retroativo de um frete esquecido, já finalizado, numa única
 * operação. Preço do diesel: abastecimentos próprios ou uma referência
 * cronologicamente ANTERIOR; nunca de uma viagem que ocorreu depois.
 */
class ViagemHistoricaTest extends ApiViagemTeste {

    private static final String HISTORICAS = VIAGENS + "/historicas";

    @Autowired
    private UsuarioRepository usuarios;

    private Conta conta;
    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        conta = registrar();
        token = conta.token();
        caminhaoId = criarCaminhao(token);
    }

    /** Frete pelo fluxo normal: 400 km carregado a 2,5 km/L, com diesel próprio se informado. */
    private long finalizada(String inicio, String fim, String valorDiesel) throws Exception {
        long id = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        if (valorDiesel != null) {
            diesel(token, id, inicio, "100", valorDiesel);
        }
        finalizar(token, id, corpo("dataFim", fim, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200);
        return id;
    }

    /** Frete histórico de R$ 1.000, 400 km carregado a 2,5 km/L (160 L), sem lançamentos. */
    private Map<String, Object> historica(String inicio, String fim) {
        return corpo("caminhaoId", caminhaoId, "dataInicio", inicio, "dataFim", fim,
                "origem", "Campinas", "destino", "Santos", "valorFrete", "1000.00",
                "kmInicialCarregado", 0, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50");
    }

    private JsonNode cadastrar(Map<String, Object> corpo, int status) throws Exception {
        return chamar(POST(HISTORICAS), token, corpo, status);
    }

    private int quantidadeDeViagens() throws Exception {
        return chamar(GET(VIAGENS), token, null, 200).size();
    }

    @Test
    void cadastraFreteHistoricoFinalizadoComLancamentos() throws Exception {
        long anterior = finalizada("2025-03-01", "2025-03-02", "600.00");
        JsonNode fotografiaAnterior = buscarViagem(token, anterior).get("rentabilidade");

        Map<String, Object> c = historica("2025-03-10", "2025-03-12");
        c.put("valorFrete", "3000.00");
        c.put("kmInicialVazio", 0);
        c.put("kmInicialCarregado", 100);
        c.put("kmFinalCarregado", 600);
        c.put("consumoMedioVazio", "4.00");
        c.put("abastecimentos", List.of(
                corpo("tipo", "DIESEL", "data", "2025-03-10", "litros", "100", "valorTotal", "650.00"),
                corpo("tipo", "ARLA", "data", "2025-03-11", "litros", "20", "valorTotal", "60.00")));
        c.put("pedagios", List.of(corpo("data", "2025-03-11", "valor", "40.00")));
        c.put("despesasExtras", List.of(corpo("descricao", "Lavagem", "data", "2025-03-12", "valor", "50.00")));
        JsonNode v = cadastrar(c, 201);

        assertEquals("FINALIZADA", v.get("status").asString());
        assertEquals("2025-03-10", v.get("dataInicio").asString());   // data real do percurso
        assertEquals("2025-03-12", v.get("dataFim").asString());
        assertFalse(v.get("cadastroRetroativoEm").isNull());           // quando foi cadastrado
        assertFalse(v.get("finalizadaEm").isNull());
        assertEquals(1, v.get("abastecimentosDiesel").size());
        assertEquals(1, v.get("abastecimentosArla").size());
        assertEquals(1, v.get("pedagios").size());
        assertEquals(1, v.get("despesasExtras").size());

        // Preço: média dos abastecimentos próprios (R$ 6,50), não o do frete anterior.
        JsonNode r = v.get("rentabilidade");
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", r.get("origemPrecoDiesel").asString());
        assertFalse(r.get("resultadoConsolidadoEm").isNull());
        dinheiro("162.50", r.get("custoDieselVazio"));        // 100 km ÷ 4 = 25 L × 6,50
        dinheiro("1300.00", r.get("custoDieselCarregado"));   // 500 km ÷ 2,5 = 200 L × 6,50
        dinheiro("1612.50", r.get("custoOperacionalTotal"));  // + Arla 60 + pedágio 40 + extra 50
        dinheiro("1387.50", r.get("lucro"));
        dinheiro("46.25", r.get("margemPercentual"));
        assertTrue(r.get("calculoCompleto").asBoolean());

        // A fotografia do frete já finalizado não muda.
        assertEquals(fotografiaAnterior, buscarViagem(token, anterior).get("rentabilidade"));
        // E o frete histórico não aceita lançamentos novos, como qualquer finalizado.
        chamar(POST(VIAGENS + "/" + v.get("id").asLong() + "/pedagios"), token,
                corpo("data", "2025-03-11", "valor", "1.00"), 409);
    }

    @Test
    void recusaSobreposicaoComQualquerViagemDoCaminhao() throws Exception {
        finalizada("2025-03-01", "2025-03-05", "600.00");
        criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-20", "kmInicialCarregado", 0));
        int antes = quantidadeDeViagens();

        for (String[] p : new String[][] {
                {"2025-03-03", "2025-03-08"},   // cruza a finalizada
                {"2025-02-20", "2025-03-02"},   // termina dentro da finalizada
                {"2025-03-01", "2025-03-01"},   // começa no mesmo dia da finalizada
                {"2025-03-15", "2025-03-21"},   // cruza a viagem em andamento (período aberto)
                {"2025-03-21", "2025-03-22"}}) { // inteira depois do início da viagem em andamento
            JsonNode erro = cadastrar(historica(p[0], p[1]), 409);
            assertTrue(erro.get("detalhes").toString().contains("sobrepõe"), erro::toString);
        }
        assertEquals(antes, quantidadeDeViagens());

        // Entre as duas, compartilhando só os dias de fronteira: aceito.
        cadastrar(historica("2025-03-05", "2025-03-20"), 201);
        assertEquals(antes + 1, quantidadeDeViagens());
    }

    @Test
    void semDieselProprioUsaSoReferenciaAnterior() throws Exception {
        long anterior = finalizada("2025-03-01", "2025-03-02", "600.00");   // R$ 6,00/L
        long posterior = finalizada("2025-03-20", "2025-03-22", "700.00");  // R$ 7,00/L
        JsonNode fotografiaPosterior = buscarViagem(token, posterior).get("rentabilidade");

        JsonNode r = cadastrar(historica("2025-03-10", "2025-03-12"), 201).get("rentabilidade");
        assertEquals("ULTIMO_PRECO_DO_CAMINHAO", r.get("origemPrecoDiesel").asString());
        assertEquals(anterior, r.get("viagemReferenciaPrecoDieselId").asLong());
        dinheiro("6.00", r.get("precoMedioDiesel"));
        dinheiro("960.00", r.get("custoDieselCarregado"));
        dinheiro("40.00", r.get("lucro"));

        // O frete posterior (que agora tem um frete anterior a ele) não é recalculado.
        assertEquals(fotografiaPosterior, buscarViagem(token, posterior).get("rentabilidade"));
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", fotografiaPosterior.get("origemPrecoDiesel").asString());
    }

    @Test
    void nuncaUsaPrecoDeViagemPosterior() throws Exception {
        finalizada("2025-03-20", "2025-03-22", "700.00");   // só existe uma viagem DEPOIS da histórica

        JsonNode r = cadastrar(historica("2025-03-10", "2025-03-12"), 201).get("rentabilidade");
        assertEquals("SEM_REFERENCIA", r.get("origemPrecoDiesel").asString());
        assertTrue(r.get("precoMedioDiesel").isNull());
        assertTrue(r.get("custoDieselCarregado").isNull());
        assertTrue(r.get("lucro").isNull());
        assertFalse(r.get("calculoCompleto").asBoolean());
    }

    @Test
    void semNenhumaReferenciaFicaIncompletoSemCustoInventado() throws Exception {
        JsonNode v = cadastrar(historica("2025-03-10", "2025-03-12"), 201);
        JsonNode r = v.get("rentabilidade");
        assertEquals("FINALIZADA", v.get("status").asString());
        assertEquals("SEM_REFERENCIA", r.get("origemPrecoDiesel").asString());
        assertTrue(r.get("custoDieselCarregado").isNull());
        assertTrue(r.get("custoOperacionalTotal").isNull());
        assertTrue(r.get("lucro").isNull());
        assertTrue(r.get("margemPercentual").isNull());
        assertFalse(r.get("calculoCompleto").asBoolean());
        assertTrue(r.get("pendencias").toString().contains("Sem referência de preço"), r::toString);
        dinheiro("160.000", r.get("litrosEstimadosCarregado"));

        // Fica fora dos totais oficiais, apresentado como incompleto.
        JsonNode ind = chamar(GET(VIAGENS + "/indicadores?ano=2025&mes=3"), token, null, 200);
        assertEquals(0, ind.get("viagensConsideradas").asInt());
        assertEquals(1, ind.get("viagensIncompletas").asInt());
        dinheiro("0.00", ind.get("receitaTotal"));
        dinheiro("1000.00", ind.get("valorFreteIncompletasNaoIncluido"));
    }

    @Test
    void aplicaAsValidacoesDeFreteFinalizadoEDosLancamentos() throws Exception {
        int antes = quantidadeDeViagens();

        // Dados obrigatórios de viagem finalizada.
        Map<String, Object> semConsumo = historica("2025-03-10", "2025-03-12");
        semConsumo.remove("consumoMedioCarregado");
        cadastrar(semConsumo, 400);
        Map<String, Object> semDataFim = historica("2025-03-10", "2025-03-12");
        semDataFim.remove("dataFim");
        cadastrar(semDataFim, 400);
        Map<String, Object> vazioSemConsumo = historica("2025-03-10", "2025-03-12");
        vazioSemConsumo.put("kmInicialVazio", 0);
        vazioSemConsumo.put("kmInicialCarregado", 50);
        cadastrar(vazioSemConsumo, 400);

        // Datas e odômetro.
        cadastrar(historica("2025-03-12", "2025-03-10"), 400);
        cadastrar(historica("2025-03-10", "2099-01-01"), 400);
        Map<String, Object> odometro = historica("2025-03-10", "2025-03-12");
        odometro.put("kmFinalCarregado", 0);
        cadastrar(odometro, 400);

        // Plausibilidade do consumo.
        Map<String, Object> consumo = historica("2025-03-10", "2025-03-12");
        consumo.put("consumoMedioCarregado", "99.99");
        cadastrar(consumo, 400);

        // Lançamentos: data fora do período, diesel implausível, acima do tanque, alimentação, valor zero.
        for (Map<String, Object> extra : List.of(
                Map.<String, Object>of("pedagios", List.of(corpo("data", "2025-03-13", "valor", "10.00"))),
                Map.<String, Object>of("abastecimentos", List.of(
                        corpo("tipo", "DIESEL", "data", "2025-03-10", "litros", "500", "valorTotal", "0.01"))),
                Map.<String, Object>of("abastecimentos", List.of(
                        corpo("tipo", "DIESEL", "data", "2025-03-10", "litros", "601", "valorTotal", "3606.00"))),
                Map.<String, Object>of("abastecimentos", List.of(
                        corpo("tipo", "ARLA", "data", "2025-03-10", "litros", "0", "valorTotal", "10.00"))),
                Map.<String, Object>of("despesasExtras", List.of(
                        corpo("descricao", "Almoço do motorista", "data", "2025-03-10", "valor", "45.00"))))) {
            Map<String, Object> c = historica("2025-03-10", "2025-03-12");
            c.putAll(extra);
            cadastrar(c, 400);
        }

        // Caminhão inativo.
        long inativo = chamar(POST("/api/caminhoes"), token, corpo("placa", "HIN" + (caminhaoId % 10000),
                "modelo", "x", "ano", 2020, "status", "INATIVO"), 201).get("id").asLong();
        Map<String, Object> c = historica("2025-03-10", "2025-03-12");
        c.put("caminhaoId", inativo);
        cadastrar(c, 409);

        // Nada foi gravado.
        assertEquals(antes, quantidadeDeViagens());
    }

    @Test
    void isolamentoEPermissao() throws Exception {
        // Caminhão de outra empresa: 404, e nada é gravado em nenhuma das duas.
        String outro = registrar().token();
        long caminhaoDoOutro = criarCaminhao(outro);
        Map<String, Object> c = historica("2025-03-10", "2025-03-12");
        c.put("caminhaoId", caminhaoDoOutro);
        cadastrar(c, 404);
        assertEquals(0, quantidadeDeViagens());
        assertEquals(0, chamar(GET(VIAGENS), outro, null, 200).size());

        // Sem token: 401. MOTORISTA (perfil lido do banco): 403.
        chamar(POST(HISTORICAS), null, historica("2025-03-10", "2025-03-12"), 401);
        Usuario usuario = usuarios.findByEmail(conta.email()).orElseThrow();
        usuario.setPerfil(Perfil.MOTORISTA);
        usuarios.saveAndFlush(usuario);
        cadastrar(historica("2025-03-10", "2025-03-12"), 403);
        usuario.setPerfil(Perfil.DONO);
        usuarios.saveAndFlush(usuario);
        assertEquals(0, quantidadeDeViagens());
    }
}
