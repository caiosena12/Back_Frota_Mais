package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.JsonNode;

/**
 * Correções do DONO em viagem encerrada ficam registradas campo a campo
 * (quem, quando, valor anterior e novo), e a fotografia financeira
 * substituída continua disponível. Edições durante a viagem não são
 * auditadas: fazem parte do fluxo normal.
 */
class AuditoriaCorrecaoTest extends ApiViagemTeste {

    @Autowired
    private UsuarioRepository usuarios;

    private String dono;
    private long donoId;
    private long caminhaoId;
    private long viagem;
    private String url;
    private long dieselId;
    private long pedagioId;
    private long extraId;

    @BeforeEach
    void preparar() throws Exception {
        Conta conta = registrar();
        dono = conta.token();
        donoId = usuarios.findByEmail(conta.email()).orElseThrow().getId();
        caminhaoId = criarCaminhao(dono);
        viagem = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        url = VIAGENS + "/" + viagem;
        dieselId = diesel(dono, viagem, "2025-06-01", "100", "600.00").get("id").asLong();
        pedagioId = pedagio(dono, viagem, "2025-06-01", "10.00").get("id").asLong();
        extraId = despesaExtra(dono, viagem, "Chapa", "2025-06-02", "50.00").get("id").asLong();
    }

    private void finalizarViagem() throws Exception {
        finalizar(dono, viagem, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
    }

    private JsonNode alteracoes() throws Exception {
        return chamar(GET(url + "/alteracoes"), dono, null, 200);
    }

    private JsonNode fotografias() throws Exception {
        return chamar(GET(url + "/fotografias-anteriores"), dono, null, 200);
    }

    /** campo → [anterior, novo] das alterações de uma entidade. */
    private Map<String, String[]> porCampo(JsonNode lista, String entidade, long registroId) {
        Map<String, String[]> m = new HashMap<>();
        for (JsonNode a : lista) {
            if (a.get("entidade").asString().equals(entidade) && a.get("registroId").asLong() == registroId) {
                assertEquals(donoId, a.get("usuarioId").asLong(), a::toString);
                assertTrue(!a.get("alteradaEm").isNull(), a::toString);
                m.put(a.get("campo").asString(), new String[] {
                        a.get("valorAnterior").isNull() ? null : a.get("valorAnterior").asString(),
                        a.get("valorNovo").isNull() ? null : a.get("valorNovo").asString()});
            }
        }
        return m;
    }

    @Test
    void edicaoDuranteAViagemEFinalizacaoNaoGeramAuditoria() throws Exception {
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "origem", "X", "destino", "B",
                "valorFrete", "900.00", "kmInicialCarregado", 0), 200);
        chamar(PUT(url + "/pedagios/" + pedagioId), dono, corpo("data", "2025-06-01", "valor", "11.00"), 200);
        finalizarViagem();
        assertEquals(0, alteracoes().size());
        assertEquals(0, fotografias().size());
    }

    @Test
    void correcaoDaViagemRegistraCadaCampoEGuardaAFotografiaAnterior() throws Exception {
        finalizarViagem();
        JsonNode original = buscarViagem(dono, viagem).get("rentabilidade");

        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-02",
                "origem", "C", "destino", "B", "valorFrete", "1200.00", "kmInicialCarregado", 0,
                "kmFinalCarregado", 500, "consumoMedioCarregado", "2.50"), 200);

        Map<String, String[]> m = porCampo(alteracoes(), "VIAGEM", viagem);
        assertEquals(2, m.size(), m::toString);
        assertEquals("A", m.get("origem")[0]);
        assertEquals("C", m.get("origem")[1]);
        assertEquals("1000", m.get("valorFrete")[0]);
        assertEquals("1200", m.get("valorFrete")[1]);

        // A fotografia atual reflete a correção; a anterior está preservada como era.
        dinheiro("1200.00", buscarViagem(dono, viagem).get("rentabilidade").get("receita"));
        JsonNode anteriores = fotografias();
        assertEquals(1, anteriores.size());
        assertEquals(donoId, anteriores.get(0).get("substituidaPorId").asLong());
        JsonNode anterior = anteriores.get(0).get("resultado");
        dinheiro("1000.00", anterior.get("receita"));
        dinheiro(original.get("lucro").asString(), anterior.get("lucro"));
        assertEquals(original.get("resultadoConsolidadoEm").asString(),
                anterior.get("resultadoConsolidadoEm").asString());
    }

    @Test
    void correcaoDeLancamentosRegistraEntidadeERegistro() throws Exception {
        finalizarViagem();
        chamar(PUT(url + "/pedagios/" + pedagioId), dono, corpo("data", "2025-06-02", "valor", "12.50"), 200);
        chamar(PUT(url + "/abastecimentos/" + dieselId), dono, corpo("tipo", "DIESEL", "data", "2025-06-01",
                "litros", "100", "valorTotal", "650.00"), 200);
        chamar(PUT(url + "/despesas-extras/" + extraId), dono, corpo("descricao", "Chapa e lona",
                "data", "2025-06-02", "valor", "50.00"), 200);

        JsonNode lista = alteracoes();
        Map<String, String[]> pedagio = porCampo(lista, "PEDAGIO", pedagioId);
        assertEquals(Map.of("data", 2, "valor", 2).keySet(), pedagio.keySet());
        assertEquals("10", pedagio.get("valor")[0]);
        assertEquals("12.5", pedagio.get("valor")[1]);
        assertEquals("2025-06-01", pedagio.get("data")[0]);

        Map<String, String[]> diesel = porCampo(lista, "ABASTECIMENTO", dieselId);
        assertEquals(1, diesel.size(), diesel::toString);
        assertEquals("650", diesel.get("valorTotal")[1]);

        Map<String, String[]> extra = porCampo(lista, "DESPESA_EXTRA", extraId);
        assertEquals(1, extra.size(), extra::toString);
        assertEquals("Chapa", extra.get("descricao")[0]);
        assertEquals("Chapa e lona", extra.get("descricao")[1]);

        // Uma versão anterior por correção, em ordem cronológica.
        JsonNode anteriores = fotografias();
        assertEquals(3, anteriores.size());
        dinheiro("10.00", anteriores.get(0).get("resultado").get("pedagios"));
        dinheiro("12.50", anteriores.get(1).get("resultado").get("pedagios"));
    }

    @Test
    void correcaoRecusadaNaoDeixaRastro() throws Exception {
        finalizarViagem();
        chamar(PUT(url + "/pedagios/" + pedagioId), dono, corpo("data", "2025-06-05", "valor", "12.00"), 400);
        chamar(PUT(url), dono, corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-02",
                "origem", "A", "destino", "B", "kmInicialCarregado", 0,
                "kmFinalCarregado", 500, "consumoMedioCarregado", "2.50"), 400);
        assertEquals(0, alteracoes().size());
        assertEquals(0, fotografias().size());
    }

    @Test
    void correcaoDeViagemCanceladaTambemEAuditada() throws Exception {
        chamar(POST(url + "/cancelar"), dono, corpo("motivo", "Carga retida", "dataFim", "2025-06-02"), 200);
        chamar(PUT(url + "/pedagios/" + pedagioId), dono, corpo("data", "2025-06-01", "valor", "20.00"), 200);
        assertEquals("20", porCampo(alteracoes(), "PEDAGIO", pedagioId).get("valor")[1]);
        assertEquals(1, fotografias().size());
    }

    @Test
    void soODonoDaEmpresaConsultaAAuditoria() throws Exception {
        finalizarViagem();
        Motorista motorista = criarMotorista(dono, caminhaoId);
        chamar(GET(url), motorista.token(), null, 200);
        chamar(GET(url + "/alteracoes"), motorista.token(), null, 403);
        chamar(GET(url + "/fotografias-anteriores"), motorista.token(), null, 403);

        String outro = registrar().token();
        chamar(GET(url + "/alteracoes"), outro, null, 404);
        chamar(GET(url + "/fotografias-anteriores"), outro, null, 404);
        chamar(GET(url + "/alteracoes"), null, null, 401);
    }
}
