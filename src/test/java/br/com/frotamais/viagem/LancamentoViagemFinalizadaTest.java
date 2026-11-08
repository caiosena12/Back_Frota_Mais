package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.JsonNode;

/**
 * Regressão B2 da auditoria: viagem finalizada não aceita lançamentos novos
 * (409) nem exclusões (409); o DONO só corrige os existentes, dentro do período.
 */
class LancamentoViagemFinalizadaTest extends ApiViagemTeste {

    @Autowired private UsuarioRepository usuarios;

    private Conta conta;
    private String token;
    private long viagem;
    private String url;
    private long dieselId, arlaId, pedagioId, extraId;

    @BeforeEach
    void preparar() throws Exception {
        conta = registrar();
        token = conta.token();
        long caminhaoId = criarCaminhao(token);
        viagem = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-03-01",
                "origem", "A", "destino", "B", "valorFrete", "2000.00", "kmInicialCarregado", 0));
        url = VIAGENS + "/" + viagem;
        dieselId = diesel(token, viagem, "2025-03-01", "100", "600.00").get("id").asLong();
        arlaId = arla(token, viagem, "2025-03-01", "20", "60.00").get("id").asLong();
        pedagioId = pedagio(token, viagem, "2025-03-01", "10.00").get("id").asLong();
        extraId = despesaExtra(token, viagem, "Lavagem", "2025-03-01", "50.00").get("id").asLong();
        finalizar(token, viagem, corpo("dataFim", "2025-03-03", "kmFinalCarregado", 400,
                "consumoMedioCarregado", "2.50"), 200);
    }

    private static Map<String, Object> diesel(String data, String valor) {
        return corpo("tipo", "DIESEL", "data", data, "litros", "100", "valorTotal", valor);
    }

    @Test
    void naoAceitaLancamentoNovoDeNenhumTipo() throws Exception {
        String rentabilidadeAntes = buscarViagem(token, viagem).get("rentabilidade").toString();

        for (JsonNode erro : new JsonNode[] {
                chamar(POST(url + "/abastecimentos"), token, diesel("2025-03-02", "900.00"), 409),
                chamar(POST(url + "/abastecimentos"), token, corpo("tipo", "ARLA", "data", "2025-03-02",
                        "litros", "10", "valorTotal", "30.00"), 409),
                chamar(POST(url + "/pedagios"), token, corpo("data", "2025-03-02", "valor", "5.00"), 409),
                chamar(POST(url + "/despesas-extras"), token, corpo("descricao", "Borracharia",
                        "data", "2025-03-02", "valor", "80.00"), 409)}) {
            assertTrue(erro.get("detalhes").toString().contains("não aceita novos lançamentos"), erro::toString);
        }

        JsonNode v = buscarViagem(token, viagem);
        assertEquals(1, v.get("abastecimentosDiesel").size());
        assertEquals(1, v.get("abastecimentosArla").size());
        assertEquals(1, v.get("pedagios").size());
        assertEquals(1, v.get("despesasExtras").size());
        assertEquals(rentabilidadeAntes, v.get("rentabilidade").toString());
    }

    @Test
    void naoExcluiLancamentoDeNenhumTipo() throws Exception {
        chamar(DELETE(url + "/abastecimentos/" + dieselId), token, null, 409);
        chamar(DELETE(url + "/abastecimentos/" + arlaId), token, null, 409);
        chamar(DELETE(url + "/pedagios/" + pedagioId), token, null, 409);
        chamar(DELETE(url + "/despesas-extras/" + extraId), token, null, 409);
        JsonNode v = buscarViagem(token, viagem);
        assertEquals(4, v.get("abastecimentosDiesel").size() + v.get("abastecimentosArla").size()
                + v.get("pedagios").size() + v.get("despesasExtras").size());
    }

    @Test
    void donoCorrigeLancamentosExistentesDentroDoPeriodo() throws Exception {
        chamar(PUT(url + "/abastecimentos/" + dieselId), token, diesel("2025-03-02", "650.00"), 200);
        chamar(PUT(url + "/abastecimentos/" + arlaId), token, corpo("tipo", "ARLA", "data", "2025-03-02",
                "litros", "20", "valorTotal", "70.00"), 200);
        chamar(PUT(url + "/pedagios/" + pedagioId), token, corpo("data", "2025-03-03", "valor", "12.00"), 200);
        chamar(PUT(url + "/despesas-extras/" + extraId), token, corpo("descricao", "Lavagem completa",
                "data", "2025-03-03", "valor", "55.00"), 200);

        JsonNode r = buscarViagem(token, viagem).get("rentabilidade");
        dinheiro("6.50", r.get("precoMedioDiesel"));      // diesel corrigido recalcula a própria viagem
        dinheiro("1040.00", r.get("custoDieselCarregado"));
        dinheiro("70.00", r.get("custoArla"));
        dinheiro("12.00", r.get("pedagios"));
        dinheiro("55.00", r.get("despesasExtras"));

        // Correção não tira o lançamento do período da viagem.
        chamar(PUT(url + "/abastecimentos/" + dieselId), token, diesel("2025-03-04", "650.00"), 400);
        chamar(PUT(url + "/pedagios/" + pedagioId), token, corpo("data", "2025-02-28", "valor", "12.00"), 400);
    }

    @Test
    void motoristaNaoCorrigeLancamentoDeViagemFinalizada() throws Exception {
        Usuario usuario = usuarios.findByEmail(conta.email()).orElseThrow();
        usuario.setPerfil(Perfil.MOTORISTA);
        usuarios.saveAndFlush(usuario);

        chamar(PUT(url + "/pedagios/" + pedagioId), token, corpo("data", "2025-03-01", "valor", "1.00"), 403);
        chamar(PUT(url + "/abastecimentos/" + dieselId), token, diesel("2025-03-01", "650.00"), 403);
    }

    @Test
    void viagemEmAndamentoContinuaAceitandoInclusaoEExclusao() throws Exception {
        long caminhao = criarCaminhao(token);
        long aberta = criarViagem(token, corpo("caminhaoId", caminhao, "dataInicio", "2025-03-01",
                "kmInicialCarregado", 0));
        long p = pedagio(token, aberta, "2025-03-01", "10.00").get("id").asLong();
        chamar(DELETE(VIAGENS + "/" + aberta + "/pedagios/" + p), token, null, 204);
    }
}
