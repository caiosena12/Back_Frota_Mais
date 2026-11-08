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
 * transação, que de fato gravam (commit) no banco frotamais_test. Por isso
 * esta classe NÃO usa a transação de teste com rollback da classe base.
 *
 * Os dados são isolados (empresa, usuário e caminhão novos, só deste teste)
 * e apagados ao final por id da empresa criada aqui, nunca de forma
 * indiscriminada. GuardaBancoDeTeste continua ativo, e a limpeza confere de
 * novo o nome do banco antes de apagar.
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
        assertEquals("frotamais_test", jdbc.queryForObject("select current_database()", String.class));
        String daEmpresa = "select id from viagens where empresa_id = ?";
        jdbc.update("delete from abastecimentos where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from despesas_viagem where viagem_id in (" + daEmpresa + ")", empresaId);
        jdbc.update("delete from viagens where empresa_id = ?", empresaId);
        jdbc.update("delete from caminhoes where empresa_id = ?", empresaId);
        jdbc.update("delete from usuarios where empresa_id = ?", empresaId);
        jdbc.update("delete from empresas where id = ?", empresaId);

        for (String sql : List.of("select count(*) from viagens where empresa_id = ?",
                "select count(*) from caminhoes where empresa_id = ?",
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
        return POST(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(corpo));
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
}
