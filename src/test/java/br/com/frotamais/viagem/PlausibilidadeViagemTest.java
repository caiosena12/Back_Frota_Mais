package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.JsonNode;

/**
 * Regressão B1 da auditoria: valores implausíveis são recusados na entrada e,
 * se já estiverem gravados, não produzem rentabilidade "completa".
 * Limites padrão: diesel de R$ 1,00 a R$ 30,00/L; consumo de 0,50 a 10,00 km/L.
 */
class PlausibilidadeViagemTest extends ApiViagemTeste {

    @Autowired private AbastecimentoRepository abastecimentos;
    @Autowired private ViagemRepository viagens;
    @Autowired private UsuarioRepository usuarios;

    private Conta conta;
    private String token;
    private long caminhaoId;   // capacidade do tanque: 600 L

    @BeforeEach
    void preparar() throws Exception {
        conta = registrar();
        token = conta.token();
        caminhaoId = criarCaminhao(token);
    }

    private long abrir(String inicio) throws Exception {
        return criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
    }

    private JsonNode abastecer(long viagem, String tipo, String litros, String valor, int status) throws Exception {
        return chamar(POST(VIAGENS + "/" + viagem + "/abastecimentos"), token,
                corpo("tipo", tipo, "data", "2025-03-01", "litros", litros, "valorTotal", valor), status);
    }

    @Test
    void precoDoDieselForaDosLimitesERecusado() throws Exception {
        long id = abrir("2025-03-01");
        JsonNode erro = abastecer(id, "DIESEL", "500", "0.01", 400);           // cenário da auditoria
        assertTrue(erro.get("detalhes").toString().contains("plausibilidade"), erro::toString);
        abastecer(id, "DIESEL", "100", "1.00", 400);                         // R$ 0,01/L
        abastecer(id, "DIESEL", "100", "99.99", 400);                        // R$ 0,9999/L
        abastecer(id, "DIESEL", "100", "3000.01", 400);                      // R$ 30,0001/L
        abastecer(id, "DIESEL", "100", "100.00", 201);                       // R$ 1,00/L (limite)
        abastecer(id, "DIESEL", "100", "3000.00", 201);                      // R$ 30,00/L (limite)

        // A correção (PUT) também é validada.
        long ab = abastecer(id, "DIESEL", "100", "600.00", 201).get("id").asLong();
        chamar(PUT(VIAGENS + "/" + id + "/abastecimentos/" + ab), token, corpo("tipo", "DIESEL",
                "data", "2025-03-01", "litros", "600", "valorTotal", "100.00"), 400);   // litros e valor trocados

        // Arla não segue a faixa de preço do diesel.
        abastecer(id, "ARLA", "100", "50.00", 201);
    }

    @Test
    void consumoForaDosLimitesERecusado() throws Exception {
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0, "consumoMedioCarregado", "99.99"), 400);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01",
                "kmInicialVazio", 0, "consumoMedioVazio", "0.49"), 400);

        long id = abrir("2025-03-01");
        diesel(token, id, "2025-03-01", "100", "600.00");
        finalizar(token, id, corpo("dataFim", "2025-03-01", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "10.01"), 400);
        finalizar(token, id, corpo("dataFim", "2025-03-01", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "99.99"), 400);
        assertEquals("EM_ANDAMENTO", buscarViagem(token, id).get("status").asString());

        JsonNode r = finalizar(token, id, corpo("dataFim", "2025-03-01", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "10.00"), 200).get("rentabilidade");
        assertTrue(r.get("calculoCompleto").asBoolean());
        dinheiro("240.00", r.get("custoDieselCarregado"));   // 40 L × 6,00
    }

    @Test
    void capacidadeDoTanqueLimitaLitrosDeDieselQuandoCadastrada() throws Exception {
        long id = abrir("2025-03-01");
        abastecer(id, "DIESEL", "600", "3600.00", 201);
        JsonNode erro = abastecer(id, "DIESEL", "600.001", "3600.01", 400);
        assertTrue(erro.get("detalhes").toString().contains("capacidade do tanque"), erro::toString);
        abastecer(id, "ARLA", "700", "2000.00", 201);   // Arla tem tanque próprio

        // Caminhão sem capacidade cadastrada: não há limite inventado.
        long semCapacidade = chamar(POST("/api/caminhoes"), token, corpo("placa", "SCP" + (caminhaoId % 10000),
                "modelo", "x", "ano", 2020), 201).get("id").asLong();
        long outra = criarViagem(token, corpo("caminhaoId", semCapacidade, "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        abastecer(outra, "DIESEL", "5000", "30000.00", 201);
    }

    /** Grava um diesel direto no repositório, simulando dado histórico anterior aos limites. */
    private void dieselHistorico(long viagemId, String litros, String valor) {
        Abastecimento a = new Abastecimento();
        a.setViagem(viagens.findByIdAndEmpresaId(viagemId, usuarios.findByEmail(conta.email()).orElseThrow()
                .getEmpresa().getId()).orElseThrow());
        a.setCriadoPor(usuarios.findByEmail(conta.email()).orElseThrow());
        a.setTipo(TipoCombustivel.DIESEL);
        a.setData(LocalDate.of(2025, 3, 3));
        a.setLitros(new BigDecimal(litros));
        a.setValorTotal(new BigDecimal(valor));
        abastecimentos.saveAndFlush(a);
    }

    @Test
    void referenciaHistoricaImplausivelEPuladaOuSinalizada() throws Exception {
        // G: preço válido (R$ 6,00/L).
        long g = abrir("2025-03-01");
        diesel(token, g, "2025-03-01", "100", "600.00");
        finalizar(token, g, corpo("dataFim", "2025-03-02", "kmFinalCarregado", 400, "consumoMedioCarregado", "2.50"), 200);

        // H: mais recente, com diesel histórico implausível (R$ 0,50/L).
        long h = abrir("2025-03-03");
        dieselHistorico(h, "100", "50.00");
        JsonNode rh = finalizar(token, h, corpo("dataFim", "2025-03-04", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        // O preço da própria viagem não é trocado: o cálculo fica incompleto, com o motivo.
        assertEquals("ABASTECIMENTOS_DA_VIAGEM", rh.get("origemPrecoDiesel").asString());
        assertFalse(rh.get("calculoCompleto").asBoolean());
        assertTrue(rh.get("lucro").isNull());
        assertTrue(rh.get("pendencias").toString().contains("fora do limite"), rh::toString);

        // N: sem diesel. O "último preço" pula H (implausível) e usa G.
        long n = abrir("2025-03-05");
        JsonNode rn = finalizar(token, n, corpo("dataFim", "2025-03-06", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200).get("rentabilidade");
        assertEquals("ULTIMO_PRECO_DO_CAMINHAO", rn.get("origemPrecoDiesel").asString());
        assertEquals(g, rn.get("viagemReferenciaPrecoDieselId").asLong());
        assertTrue(rn.get("calculoCompleto").asBoolean());

        // Fretes com cálculo incompleto ficam fora dos totais.
        JsonNode ind = chamar(GET(VIAGENS + "/indicadores?ano=2025&mes=3"), token, null, 200);
        assertEquals(3, ind.get("viagensFinalizadas").asInt());
        assertEquals(2, ind.get("viagensConsideradas").asInt());
        assertEquals(h, ind.get("viagensComCalculoIncompleto").get(0).asLong());
    }
}
