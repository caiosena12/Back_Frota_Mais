package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;

/**
 * Quilometragem (odômetro) no abastecimento, contratos 1.2: obrigatória nos
 * abastecimentos novos (lançamento, abertura e viagem histórica), validada,
 * gravada e devolvida; registros antigos continuam sem ela (nula, nunca zero).
 * Também confere que pedágio e despesa extra são gravados e consultados.
 *
 * O EntityManager é esvaziado antes de ler o banco com SQL, para que a consulta
 * veja o que foi de fato gravado, e não só o objeto em memória.
 */
class QuilometragemAbastecimentoTest extends ApiViagemTeste {

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    private String dono;
    private long caminhaoId;
    private long viagemId;
    private String url;

    @BeforeEach
    void preparar() throws Exception {
        dono = registrar().token();
        caminhaoId = criarCaminhao(dono);
        viagemId = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "Campinas", "destino", "Santos", "valorFrete", "5000.00", "kmInicialCarregado", 150000));
        url = VIAGENS + "/" + viagemId;
    }

    private static Map<String, Object> abastecimento(String tipo, String data, Object km, String litros, String valor) {
        return corpo("tipo", tipo, "data", data, "quilometragem", km, "litros", litros, "valorTotal", valor);
    }

    private Map<String, Object> linha(String tabela, long id) {
        em.flush();
        em.clear();
        return jdbc.queryForMap("SELECT * FROM " + tabela + " WHERE id = ?", id);
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    // ---------- cadastro, persistência e consulta ----------

    @Test
    void dieselEArlaGravamEDevolvemAQuilometragem() throws Exception {
        JsonNode d = chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("DIESEL", "2025-06-02", 152430, "100", "600.00"), 201);
        JsonNode a = chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("ARLA", "2025-06-02", 152431, "20", "80.00"), 201);
        assertEquals(152430, d.get("quilometragem").asInt());
        assertTrue(d.get("hora").isNull(), d::toString);

        Map<String, Object> linhaDiesel = linha("abastecimentos", d.get("id").asLong());
        assertEquals(152430, linhaDiesel.get("quilometragem"));
        assertEquals(viagemId, ((Number) linhaDiesel.get("viagem_id")).longValue());
        assertNull(linhaDiesel.get("hora"));
        assertEquals(152431, linha("abastecimentos", a.get("id").asLong()).get("quilometragem"));

        JsonNode v = buscarViagem(dono, viagemId);
        assertEquals(152430, v.get("abastecimentosDiesel").get(0).get("quilometragem").asInt());
        assertEquals(152431, v.get("abastecimentosArla").get(0).get("quilometragem").asInt());
        dinheiro("600.00", v.get("abastecimentosDiesel").get(0).get("valorTotal"));
        dinheiro("20", v.get("abastecimentosArla").get(0).get("litros"));
    }

    @Test
    void abastecimentosParciaisNaMesmaViagemMantemPrecoMedioPonderado() throws Exception {
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("DIESEL", "2025-06-01", 150010, "100", "600.00"), 201);
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("DIESEL", "2025-06-02", 150380, "50", "300.00"), 201);
        finalizar(dono, viagemId, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 150450,
                "consumoMedioCarregado", "2.50"), 200);

        JsonNode v = buscarViagem(dono, viagemId);
        JsonNode lista = v.get("abastecimentosDiesel");
        assertEquals(2, lista.size());
        assertEquals(150010, lista.get(0).get("quilometragem").asInt());
        assertEquals(150380, lista.get(1).get("quilometragem").asInt());
        // (600 + 300) / 150 l: a quilometragem não entra no cálculo.
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", v.get("rentabilidade").get("origemPrecoDiesel").asString());
        dinheiro("6.00", v.get("rentabilidade").get("precoMedioDiesel"));
    }

    @Test
    void zeroEOLimiteMaximoSaoAceitos() throws Exception {
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("ARLA", "2025-06-02", 0, "10", "40.00"), 201);
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("ARLA", "2025-06-02", 9_999_999, "10", "40.00"), 201);
    }

    // ---------- validação ----------

    @Test
    void novoAbastecimentoSemQuilometragemERecusadoENadaEGravado() throws Exception {
        erro(chamar(POST(url + "/abastecimentos"), dono,
                corpo("tipo", "DIESEL", "data", "2025-06-02", "litros", "100", "valorTotal", "600.00"), 400),
                "Informe a quilometragem do caminhão");
        erro(chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("ARLA", "2025-06-02", null, "10", "40.00"), 400), "quilometragem");
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosDiesel").size());
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosArla").size());
    }

    @Test
    void quilometragemInvalidaERecusada() throws Exception {
        String lancar = url + "/abastecimentos";
        erro(chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", -1, "100", "600.00"), 400),
                "quilometragem: deve ser maior ou igual a zero");
        erro(chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", 10_000_000, "100", "600.00"), 400),
                "quilometragem: deve ser menor ou igual a 9999999");
        chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", "152.430,5", "100", "600.00"), 400);
        // Decimal, texto e texto numérico não são truncados nem convertidos: 400.
        erro(chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", 1.5, "100", "600.00"), 400),
                "Campo 'quilometragem' com valor inválido.");
        chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", "abc", "100", "600.00"), 400);
        chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", "152430", "100", "600.00"), 400);
        chamar(POST(lancar), dono, abastecimento("DIESEL", "2025-06-02", 3_000_000_000L, "100", "600.00"), 400);
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosDiesel").size());
    }

    /** Contratos 1.2: hora saiu do request. Cliente antigo que a envia recebe 400 (campo desconhecido), sem gravar. */
    @Test
    void horaEnviadaERecusadaComoCampoDesconhecido() throws Exception {
        Map<String, Object> c = abastecimento("DIESEL", "2025-06-02", 152430, "100", "600.00");
        c.put("hora", "14:30");
        erro(chamar(POST(url + "/abastecimentos"), dono, c, 400), "Campo desconhecido: 'hora'.");
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosDiesel").size());
    }

    /**
     * A data real do abastecimento não é deduzida de um instante: data com horário
     * (como a de new Date().toISOString()) dá 400, em vez de ser gravada num dia
     * que pode ser outro (em UTC) ou cair no futuro.
     */
    @Test
    void dataComHorarioERecusadaSemGravar() throws Exception {
        for (String data : List.of("2025-06-02T14:30:00", "2025-06-03T01:30:00.000Z")) {
            erro(chamar(POST(url + "/abastecimentos"), dono, abastecimento("ARLA", data, 152430, "20", "80.00"), 400),
                    "Campo 'data' com valor inválido. Use o formato AAAA-MM-DD.");
        }
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosArla").size());
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("ARLA", "2025-06-02", 152430, "20", "80.00"), 201);
        assertEquals("2025-06-02",
                buscarViagem(dono, viagemId).get("abastecimentosArla").get(0).get("data").asString());
    }

    @Test
    void bancoRecusaQuilometragemNegativa() throws Exception {
        long id = chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("DIESEL", "2025-06-02", 152430, "100", "600.00"), 201).get("id").asLong();
        em.flush();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE abastecimentos SET quilometragem = -5 WHERE id = ?", id));
    }

    // ---------- abertura e viagem histórica ----------

    @Test
    void aberturaComAbastecimentosAnterioresExigeEGravaAQuilometragem() throws Exception {
        finalizar(dono, viagemId, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 150450,
                "consumoMedioCarregado", "2.50"), 200);
        Map<String, Object> abertura = corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-10",
                "origem", "Santos", "destino", "Campinas", "valorFrete", "4000.00", "kmInicialCarregado", 150900);

        // Um item sem quilometragem invalida a abertura inteira: nada é criado.
        abertura.put("abastecimentos", List.of(
                abastecimento("DIESEL", "2025-06-05", 150600, "200", "1200.00"),
                corpo("tipo", "ARLA", "data", "2025-06-06", "litros", "20", "valorTotal", "70.00")));
        erro(chamar(POST(VIAGENS), dono, abertura, 400), "Informe a quilometragem do caminhão");
        assertEquals(1, chamar(GET(VIAGENS + "?caminhaoId=" + caminhaoId), dono, null, 200).size());

        abertura.put("abastecimentos", List.of(
                abastecimento("DIESEL", "2025-06-05", 150600, "200", "1200.00"),
                abastecimento("ARLA", "2025-06-06", 150650, "20", "70.00")));
        JsonNode v = chamar(POST(VIAGENS), dono, abertura, 201);
        // Datas reais preservadas, atribuídos à nova viagem, com a quilometragem.
        JsonNode d = v.get("abastecimentosDiesel").get(0);
        assertEquals("2025-06-05", d.get("data").asString());
        assertEquals(150600, d.get("quilometragem").asInt());
        assertEquals(150650, v.get("abastecimentosArla").get(0).get("quilometragem").asInt());
        assertEquals(150600, linha("abastecimentos", d.get("id").asLong()).get("quilometragem"));
        // A viagem anterior não recebeu nada.
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosDiesel").size());
    }

    @Test
    void viagemHistoricaExigeQuilometragemNosAbastecimentos() throws Exception {
        Map<String, Object> h = corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01", "dataFim", "2025-03-03",
                "origem", "X", "destino", "Y", "valorFrete", "900.00", "kmInicialCarregado", 140000,
                "kmFinalCarregado", 140300, "consumoMedioCarregado", "2.50");
        h.put("abastecimentos", List.of(corpo("tipo", "DIESEL", "data", "2025-03-01", "litros", "100",
                "valorTotal", "600.00")));
        erro(chamar(POST(VIAGENS + "/historicas"), dono, h, 400), "Informe a quilometragem do caminhão");

        h.put("abastecimentos", List.of(abastecimento("DIESEL", "2025-03-01", 140010, "100", "600.00")));
        JsonNode v = chamar(POST(VIAGENS + "/historicas"), dono, h, 201);
        assertEquals(140010, v.get("abastecimentosDiesel").get(0).get("quilometragem").asInt());
    }

    // ---------- registros antigos ----------

    /** Simula um abastecimento gravado antes da V12: sem quilometragem, com o horário antigo. */
    private long abastecimentoAntigo() throws Exception {
        long id = chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("DIESEL", "2025-06-02", 152430, "100", "600.00"), 201).get("id").asLong();
        em.flush();
        jdbc.update("UPDATE abastecimentos SET quilometragem = NULL, hora = '14:30' WHERE id = ?", id);
        em.clear();
        return id;
    }

    @Test
    void registroAntigoSemQuilometragemContinuaConsultavelComNulo() throws Exception {
        abastecimentoAntigo();
        JsonNode a = buscarViagem(dono, viagemId).get("abastecimentosDiesel").get(0);
        assertTrue(a.get("quilometragem").isNull(), a::toString);
        assertEquals("14:30:00", a.get("hora").asString());
        dinheiro("600.00", a.get("valorTotal"));
    }

    @Test
    void correcaoDeRegistroAntigoPodeManterAQuilometragemDesconhecida() throws Exception {
        long id = abastecimentoAntigo();
        JsonNode a = chamar(PUT(url + "/abastecimentos/" + id), dono, corpo("tipo", "DIESEL", "data", "2025-06-02",
                "litros", "100", "valorTotal", "650.00"), 200);
        assertTrue(a.get("quilometragem").isNull(), a::toString);
        assertEquals("14:30:00", a.get("hora").asString());
        Map<String, Object> linha = linha("abastecimentos", id);
        assertNull(linha.get("quilometragem"));

        // Informar depois é permitido; a partir daí ela não pode ser apagada.
        assertEquals(152400, chamar(PUT(url + "/abastecimentos/" + id), dono,
                abastecimento("DIESEL", "2025-06-02", 152400, "100", "650.00"), 200).get("quilometragem").asInt());
        erro(chamar(PUT(url + "/abastecimentos/" + id), dono, corpo("tipo", "DIESEL", "data", "2025-06-02",
                "litros", "100", "valorTotal", "650.00"), 400), "Informe a quilometragem do caminhão");
    }

    @Test
    void correcaoEmViagemEncerradaAuditaAQuilometragem() throws Exception {
        long id = chamar(POST(url + "/abastecimentos"), dono,
                abastecimento("DIESEL", "2025-06-02", 152430, "100", "600.00"), 201).get("id").asLong();
        finalizar(dono, viagemId, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 150450,
                "consumoMedioCarregado", "2.50"), 200);
        chamar(PUT(url + "/abastecimentos/" + id), dono,
                abastecimento("DIESEL", "2025-06-02", 152340, "100", "600.00"), 200);

        JsonNode alteracoes = chamar(GET(url + "/alteracoes"), dono, null, 200);
        assertEquals(1, alteracoes.size(), alteracoes::toString);
        JsonNode a = alteracoes.get(0);
        assertEquals("ABASTECIMENTO", a.get("entidade").asString());
        assertEquals("quilometragem", a.get("campo").asString());
        assertEquals("152430", a.get("valorAnterior").asString());
        assertEquals("152340", a.get("valorNovo").asString());
    }

    // ---------- perfis e isolamento ----------

    @Test
    void motoristaVinculadoLancaComQuilometragemSemVerValoresFinanceiros() throws Exception {
        Motorista m = criarMotorista(dono, caminhaoId);
        chamar(POST(url + "/abastecimentos"), m.token(), abastecimento("DIESEL", "2025-06-02", 150300, "100", "600.00"), 201);
        chamar(POST(url + "/pedagios"), m.token(), corpo("data", "2025-06-02", "valor", "45.90"), 201);
        chamar(POST(url + "/despesas-extras"), m.token(),
                corpo("descricao", "Alimentação", "data", "2025-06-02", "valor", "38.50"), 201);

        JsonNode v = buscarViagem(m.token(), viagemId);
        assertEquals(150300, v.get("abastecimentosDiesel").get(0).get("quilometragem").asInt());
        // Custo, lucro e margem não são enviados ao MOTORISTA (rentabilidade nula).
        assertTrue(v.get("rentabilidade").isNull(), v::toString);
    }

    @Test
    void motoristaNaoLancaEmCaminhaoNaoVinculadoNemOutraEmpresa() throws Exception {
        long outroCaminhao = criarCaminhao(dono);
        long outraViagem = criarViagem(dono, corpo("caminhaoId", outroCaminhao, "dataInicio", "2025-06-01",
                "kmInicialCarregado", 1000));
        Motorista m = criarMotorista(dono, caminhaoId);
        Map<String, Object> c = abastecimento("DIESEL", "2025-06-02", 1100, "100", "600.00");
        chamar(POST(VIAGENS + "/" + outraViagem + "/abastecimentos"), m.token(), c, 404);
        chamar(POST(VIAGENS + "/" + outraViagem + "/pedagios"), m.token(), corpo("data", "2025-06-02", "valor", "10.00"), 404);

        String outraEmpresa = registrar().token();
        chamar(POST(url + "/abastecimentos"), outraEmpresa, c, 404);
        chamar(POST(url + "/despesas-extras"), outraEmpresa,
                corpo("descricao", "x", "data", "2025-06-02", "valor", "1.00"), 404);
        assertEquals(0, buscarViagem(dono, viagemId).get("abastecimentosDiesel").size());
        assertEquals(0, buscarViagem(dono, outraViagem).get("abastecimentosDiesel").size());
    }

    // ---------- pedágio e despesa extra ----------

    @Test
    void pedagioEDespesaExtraSaoGravadosEConsultados() throws Exception {
        JsonNode p = chamar(POST(url + "/pedagios"), dono, corpo("data", "2025-06-02", "valor", "45.90"), 201);
        JsonNode livre = chamar(POST(url + "/despesas-extras"), dono,
                corpo("descricao", "Lavagem e lona", "data", "2025-06-02", "valor", "120.00"), 201);
        JsonNode alimentacao = chamar(POST(url + "/despesas-extras"), dono,
                corpo("descricao", "Alimentação do motorista", "data", "2025-06-02", "valor", "38.50"), 201);
        assertEquals(Map.of("id", 1, "data", 1, "valor", 1).keySet(),
                objectMapper.convertValue(p, Map.class).keySet());

        Map<String, Object> linhaP = linha("despesas_viagem", p.get("id").asLong());
        assertEquals("PEDAGIO", linhaP.get("tipo"));
        assertEquals(viagemId, ((Number) linhaP.get("viagem_id")).longValue());
        assertEquals("Alimentação do motorista", linha("despesas_viagem", alimentacao.get("id").asLong()).get("descricao"));

        JsonNode v = buscarViagem(dono, viagemId);
        dinheiro("45.90", v.get("pedagios").get(0).get("valor"));
        assertEquals("2025-06-02", v.get("pedagios").get(0).get("data").asString());
        assertEquals(2, v.get("despesasExtras").size());
        assertEquals(livre.get("id").asLong(), v.get("despesasExtras").get(0).get("id").asLong());
        assertEquals("Alimentação do motorista", v.get("despesasExtras").get(1).get("descricao").asString());
    }

    @Test
    void pedagioEDespesaEntramNoCustoDoFrete() throws Exception {
        chamar(POST(url + "/abastecimentos"), dono, abastecimento("DIESEL", "2025-06-01", 150010, "100", "600.00"), 201);
        pedagio(dono, viagemId, "2025-06-02", "45.90");
        despesaExtra(dono, viagemId, "Alimentação", "2025-06-02", "38.50");
        finalizar(dono, viagemId, corpo("dataFim", "2025-06-03", "kmFinalCarregado", 150250,
                "consumoMedioCarregado", "2.50"), 200);
        JsonNode r = buscarViagem(dono, viagemId).get("rentabilidade");
        // Diesel: 250 km / 2,5 = 100 l x R$ 6,00 = 600,00; + 45,90 + 38,50.
        dinheiro("45.90", r.get("pedagios"));
        dinheiro("38.50", r.get("despesasExtras"));
        dinheiro("684.40", r.get("custoOperacionalTotal"));
        dinheiro("4315.60", r.get("lucro"));
    }
}
