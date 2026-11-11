package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concorrência real: requisições simultâneas, cada uma na sua thread e na sua
 * transação, que de fato gravam (commit) no banco frotamais_test_flyway. Por isso
 * esta classe NÃO usa a transação de teste com rollback da classe base.
 *
 * Os dados são isolados (empresa, usuário e caminhão novos, só deste teste)
 * e apagados ao final por id da empresa criada aqui, nunca de forma
 * indiscriminada. GuardaBancoDeTeste continua ativo, e a limpeza confere de
 * novo o nome do banco antes de apagar. A limpeza cobre todas as tabelas que
 * os cenários gravam (viagens, lançamentos, auditoria, manutenções e
 * caminhões), sempre filtradas pela empresa criada aqui.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcorrenciaViagemTest extends ApiViagemTeste {

    private static final int SIMULTANEAS = 8;

    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private String token;
    private long caminhaoId;

    @BeforeEach
    void preparar() throws Exception {
        Conta conta = registrar();
        token = conta.token();
        empresaId = jdbc.queryForObject("select empresa_id from usuarios where email = ?", Long.class, conta.email());
        caminhaoId = criarCaminhao(token);
    }

    @AfterEach
    void limpar() {
        if (empresaId == null) {
            return;
        }
        assertEquals("frotamais_test_flyway", jdbc.queryForObject("select current_database()", String.class));
        String daEmpresa = "select id from viagens where empresa_id = ?";
        jdbc.update("delete from viagem_alteracoes where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from viagem_fotografias_anteriores where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from abastecimentos where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from despesas_viagem where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from viagens where empresa_id = ?", empresaId);
        jdbc.update("delete from manutencoes where empresa_id = ?", empresaId);
        jdbc.update("delete from caminhoes where empresa_id = ?", empresaId);
        jdbc.update("delete from usuarios where empresa_id = ?", empresaId);
        jdbc.update("delete from empresas where id = ?", empresaId);

        for (String sql : List.of("select count(*) from viagens where empresa_id = ?",
                "select count(*) from caminhoes where empresa_id = ?",
                "select count(*) from manutencoes where empresa_id = ?",
                "select count(*) from usuarios where empresa_id = ?",
                "select count(*) from empresas where id = ?")) {
            assertEquals(0, jdbc.queryForObject(sql, Integer.class, empresaId), sql);
        }
    }

    /** Dispara as requisições ao mesmo tempo (barreira) e devolve os status HTTP. */
    private List<Integer> simultaneas(List<MockHttpServletRequestBuilder> requisicoes) throws Exception {
        CyclicBarrier largada = new CyclicBarrier(requisicoes.size());
        ExecutorService pool = Executors.newFixedThreadPool(requisicoes.size());
        try {
            List<Future<Integer>> respostas = new ArrayList<>();
            for (MockHttpServletRequestBuilder req : requisicoes) {
                respostas.add(pool.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(req).andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> f : respostas) {
                status.add(f.get(60, TimeUnit.SECONDS));
            }
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    private MockHttpServletRequestBuilder post(String url, Object corpo) throws Exception {
        return comToken(POST(url), corpo);
    }

    private MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder req, Object corpo) throws Exception {
        req.header("Authorization", "Bearer " + token);
        return corpo == null ? req
                : req.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo));
    }

    /** Pares de viagens do caminhão em conflito pela regra de sobreposição, contados direto no banco. */
    private int paresSobrepostos() {
        return jdbc.queryForObject("""
                select count(*) from viagens a join viagens b on a.id < b.id
                where a.caminhao_id = ? and b.caminhao_id = a.caminhao_id
                  and (a.data_inicio = b.data_inicio
                       or ((b.data_fim is null or a.data_inicio < b.data_fim)
                           and (a.data_fim is null or a.data_fim > b.data_inicio)))
                """, Integer.class, caminhaoId);
    }

    private int viagensDoCaminhao() {
        return jdbc.queryForObject("select count(*) from viagens where caminhao_id = ? and empresa_id = ?",
                Integer.class, caminhaoId, empresaId);
    }

    @Test
    void aberturasSimultaneasNoMesmoCaminhao_soUmaVence() throws Exception {
        List<MockHttpServletRequestBuilder> reqs = new ArrayList<>();
        for (int i = 0; i < SIMULTANEAS; i++) {
            reqs.add(post(VIAGENS, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-0" + (i + 1),
                    "kmInicialVazio", 0)));
        }
        List<Integer> status = simultaneas(reqs);

        assertEquals(1, Collections.frequency(status, 201), status::toString);
        assertEquals(SIMULTANEAS - 1, Collections.frequency(status, 409), status::toString);
        assertEquals(1, viagensDoCaminhao());
        assertEquals(0, paresSobrepostos());
    }

    @Test
    void cadastrosHistoricosSimultaneosSobrepostos_soUmVence() throws Exception {
        // Todos os períodos se cruzam, mas nenhum fica em andamento: só a checagem de
        // sobreposição sob o bloqueio do caminhão impede a duplicidade.
        List<MockHttpServletRequestBuilder> reqs = new ArrayList<>();
        for (int i = 0; i < SIMULTANEAS; i++) {
            reqs.add(post(VIAGENS + "/historicas", corpo("caminhaoId", caminhaoId,
                    "dataInicio", "2025-04-0" + (i + 1), "dataFim", "2025-04-20",
                    "origem", "A", "destino", "B", "valorFrete", "1000.00",
                    "kmInicialCarregado", 0, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50",
                    "pedagios", List.of(corpo("data", "2025-04-10", "valor", "10.00")))));
        }
        List<Integer> status = simultaneas(reqs);

        assertEquals(1, Collections.frequency(status, 201), status::toString);
        assertEquals(SIMULTANEAS - 1, Collections.frequency(status, 409), status::toString);
        assertEquals(1, viagensDoCaminhao());
        assertEquals(0, paresSobrepostos());
        // Atomicidade: só os lançamentos da viagem que venceu foram gravados.
        assertEquals(1, jdbc.queryForObject("select count(*) from despesas_viagem d join viagens v"
                + " on v.id = d.viagem_id where v.empresa_id = ?", Integer.class, empresaId));
        assertTrue(status.stream().allMatch(s -> s == 201 || s == 409), status::toString);
    }

    private static final int RODADAS = 5;

    /**
     * Janela 1 de 84d8e03: abastecimento antecipado da viagem seguinte (antes do
     * início dela, depois do fim da anterior) ao mesmo tempo que a correção da
     * data final da anterior para depois dele. Os dois bloqueiam o caminhão
     * antes da viagem, então um enxerga o outro: só um passa, e nunca fica um
     * abastecimento da seguinte com data anterior ao fim da anterior.
     */
    @Test
    void abastecimentoAntecipadoECorrecaoDoFimDaAnterior_nuncaPassamOsDois() throws Exception {
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            long caminhao = criarCaminhao(token);
            long anterior = chamar(POST(VIAGENS + "/historicas"), token, corpo("caminhaoId", caminhao,
                    "dataInicio", "2025-03-01", "dataFim", "2025-03-05", "origem", "A", "destino", "B",
                    "valorFrete", "1000.00", "kmInicialCarregado", 0, "kmFinalCarregado", 400,
                    "consumoMedioCarregado", "2.50"), 201).get("id").asLong();
            long seguinte = criarViagem(token, corpo("caminhaoId", caminhao, "dataInicio", "2025-03-10",
                    "kmInicialCarregado", 400));

            List<Integer> status = simultaneas(List.of(
                    post(VIAGENS + "/" + seguinte + "/abastecimentos", corpo("tipo", "DIESEL",
                            "data", "2025-03-06", "litros", "100", "valorTotal", "600.00")),
                    comToken(PUT(VIAGENS + "/" + anterior), corpo("dataInicio", "2025-03-01",
                            "dataFim", "2025-03-08", "origem", "A", "destino", "B", "valorFrete", "1000.00",
                            "kmInicialCarregado", 0, "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"))));

            // Ou o abastecimento entra (e a correção é recusada), ou a correção entra
            // (e o abastecimento fica antes do novo fim da anterior: recusado).
            boolean abasteceu = status.get(0) == 201;
            assertEquals(abasteceu ? List.of(201, 409) : List.of(400, 200), status, status::toString);
            assertEquals(0, jdbc.queryForObject("""
                    select count(*) from abastecimentos a, viagens ant
                    where a.viagem_id = ? and ant.id = ? and a.data < ant.data_fim
                    """, Integer.class, seguinte, anterior));
        }
    }

    /**
     * Janela 2 de 84d8e03: manutenção cadastrada ao mesmo tempo que a exclusão
     * do caminhão. A manutenção bloqueia o caminhão ao conferir que ele não está
     * excluído, como a exclusão: ou ela entra antes (e a exclusão vem depois),
     * ou é recusada. Nunca fica manutenção criada depois da exclusão.
     */
    @Test
    void manutencaoEExclusaoDoCaminhao_manutencaoNuncaEntraDepoisDaExclusao() throws Exception {
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            long caminhao = criarCaminhao(token);
            List<Integer> status = simultaneas(List.of(
                    post("/api/manutencoes", corpo("caminhaoId", caminhao, "data", "2025-03-01",
                            "descricao", "Troca de óleo", "valor", "350.00")),
                    comToken(DELETE("/api/caminhoes/" + caminhao), null)));

            assertEquals(204, status.get(1), status::toString);
            assertTrue(status.get(0) == 201 || status.get(0) == 409, status::toString);
            assertEquals(status.get(0) == 201 ? 1 : 0, jdbc.queryForObject(
                    "select count(*) from manutencoes where caminhao_id = ?", Integer.class, caminhao));
            assertEquals(0, jdbc.queryForObject("""
                    select count(*) from manutencoes m join caminhoes c on c.id = m.caminhao_id
                    where c.id = ? and m.criada_em >= c.excluido_em
                    """, Integer.class, caminhao));
        }
    }
}
