package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.UsuarioRepository;

import tools.jackson.databind.JsonNode;

/**
 * MOTORISTA com tokens reais: opera as viagens dos caminhões vinculados a ele,
 * sem ver informações financeiras gerenciais, e não altera viagem encerrada.
 * O DONO indica o motorista responsável, respeitando vínculo e empresa.
 */
class MotoristaViagemTest extends ApiViagemTeste {

    @Autowired
    private UsuarioRepository usuarios;

    private String dono;
    private String emailDono;
    private long caminhaoId;
    private Motorista motorista;

    @BeforeEach
    void preparar() throws Exception {
        Conta conta = registrar();
        dono = conta.token();
        emailDono = conta.email();
        caminhaoId = criarCaminhao(dono);
        motorista = criarMotorista(dono, caminhaoId);
    }

    private long abrirComoMotorista(String inicio) throws Exception {
        return criarViagem(motorista.token(), corpo("caminhaoId", caminhaoId, "dataInicio", inicio,
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
    }

    private void erro(JsonNode resposta, String trecho) {
        assertTrue(resposta.get("detalhes").toString().contains(trecho), resposta::toString);
    }

    @Test
    void motoristaAbreLancaEFinalizaViagemDoCaminhaoVinculado() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        diesel(motorista.token(), id, "2025-06-01", "100", "600.00");
        pedagio(motorista.token(), id, "2025-06-01", "25.00");
        despesaExtra(motorista.token(), id, "Chapa", "2025-06-01", "50.00");
        finalizar(motorista.token(), id, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);

        // Ele é o responsável, e o DONO vê tudo, inclusive o resultado.
        JsonNode visaoDono = buscarViagem(dono, id);
        assertEquals(motorista.id(), visaoDono.get("motoristaId").asLong());
        assertEquals("FINALIZADA", visaoDono.get("status").asString());
        assertFalse(visaoDono.get("rentabilidade").isNull(), visaoDono::toString);
        assertEquals(1, visaoDono.get("pedagios").size());
    }

    @Test
    void motoristaNaoVeCustoLucroNemMargem() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        diesel(motorista.token(), id, "2025-06-01", "100", "600.00");

        JsonNode detalhe = buscarViagem(motorista.token(), id);
        assertTrue(detalhe.get("rentabilidade").isNull(), detalhe::toString);
        // Os lançamentos que ele mesmo faz continuam visíveis.
        assertEquals(1, detalhe.get("abastecimentosDiesel").size());

        JsonNode lista = chamar(GET(VIAGENS), motorista.token(), null, 200);
        assertEquals(1, lista.size());
        JsonNode linha = lista.get(0);
        for (String campo : List.of("custoOperacionalTotal", "lucro", "margemPercentual", "calculoCompleto")) {
            assertTrue(linha.get(campo).isNull(), campo + ": " + linha);
        }
        dinheiro("1000.00", linha.get("valorFrete"));
        chamar(GET(VIAGENS + "/indicadores"), motorista.token(), null, 403);
    }

    @Test
    void viagemEncerradaNaoEAlteradaPeloMotorista() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        long pedagio = pedagio(motorista.token(), id, "2025-06-01", "25.00").get("id").asLong();
        finalizar(motorista.token(), id, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        String url = VIAGENS + "/" + id;

        chamar(GET(url), motorista.token(), null, 200);
        erro(chamar(PUT(url), motorista.token(), corpo("dataInicio", "2025-06-01", "dataFim", "2025-06-02",
                "kmInicialCarregado", 0, "kmFinalCarregado", 600, "consumoMedioCarregado", "2.50"), 403), "dono");
        chamar(PUT(url + "/pedagios/" + pedagio), motorista.token(), corpo("data", "2025-06-01", "valor", "1.00"), 403);
        // Lançamento novo em viagem finalizada é recusado para qualquer perfil.
        chamar(POST(url + "/pedagios"), motorista.token(), corpo("data", "2025-06-01", "valor", "1.00"), 409);

        // O DONO corrige normalmente.
        chamar(PUT(url + "/pedagios/" + pedagio), dono, corpo("data", "2025-06-01", "valor", "30.00"), 200);
        dinheiro("30.00", buscarViagem(dono, id).get("pedagios").get(0).get("valor"));
    }

    @Test
    void motoristaNaoAbreViagemEmNomeDeOutroNemTrocaResponsavel() throws Exception {
        Motorista outro = criarMotorista(dono, caminhaoId);
        erro(chamar(POST(VIAGENS), motorista.token(), corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "kmInicialVazio", 0, "motoristaId", outro.id()), 403), "próprio nome");

        long id = abrirComoMotorista("2025-06-01");
        chamar(PUT(VIAGENS + "/" + id), motorista.token(), corpo("dataInicio", "2025-06-01",
                "kmInicialCarregado", 0, "motoristaId", outro.id()), 403);
        assertEquals(motorista.id(), buscarViagem(dono, id).get("motoristaId").asLong());
    }

    @Test
    void motoristaNaoAlcancaCaminhaoNaoVinculadoNemViagemHistorica() throws Exception {
        long outroCaminhao = criarCaminhao(dono);
        long viagemDoOutro = criarViagem(dono, corpo("caminhaoId", outroCaminhao, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));

        chamar(POST(VIAGENS), motorista.token(), corpo("caminhaoId", outroCaminhao, "dataInicio", "2025-06-02",
                "kmInicialVazio", 0), 404);
        chamar(GET(VIAGENS + "/" + viagemDoOutro), motorista.token(), null, 404);
        assertEquals(0, chamar(GET(VIAGENS), motorista.token(), null, 200).size());
        chamar(POST(VIAGENS + "/historicas"), motorista.token(), corpo("caminhaoId", caminhaoId,
                "dataInicio", "2025-05-01", "dataFim", "2025-05-02", "origem", "A", "destino", "B",
                "valorFrete", "1.00", "kmInicialCarregado", 0, "kmFinalCarregado", 10,
                "consumoMedioCarregado", "2.50"), 403);
    }

    @Test
    void desvincularRemoveOAcessoMasPreservaAViagem() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        chamar(PUT("/api/motoristas/" + motorista.id() + "/caminhoes"), dono, corpo("caminhaoIds", List.of()), 200);

        chamar(GET(VIAGENS + "/" + id), motorista.token(), null, 404);
        chamar(POST(VIAGENS + "/" + id + "/pedagios"), motorista.token(),
                corpo("data", "2025-06-01", "valor", "1.00"), 404);
        assertEquals(0, chamar(GET(VIAGENS), motorista.token(), null, 200).size());

        JsonNode v = buscarViagem(dono, id);
        assertEquals(motorista.id(), v.get("motoristaId").asLong());
        assertEquals(0, v.get("pedagios").size());
    }

    @Test
    void motoristaDesativadoPerdeOAcesso() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        chamar(PUT("/api/motoristas/" + motorista.id()), dono, corpo("nome", "Desligado", "ativo", false), 200);
        chamar(GET(VIAGENS + "/" + id), motorista.token(), null, 401);
    }

    @Test
    void caminhaoExcluidoSomeDaVisaoDoMotorista() throws Exception {
        long id = abrirComoMotorista("2025-06-01");
        finalizar(motorista.token(), id, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);
        chamar(DELETE("/api/caminhoes/" + caminhaoId), dono, null, 204);

        assertEquals(0, chamar(GET(VIAGENS), motorista.token(), null, 200).size());
        chamar(GET(VIAGENS + "/" + id), motorista.token(), null, 404);
        assertEquals("FINALIZADA", buscarViagem(dono, id).get("status").asString());
    }

    @Test
    void donoIndicaMotoristaVinculadoASiMesmoOuNinguem() throws Exception {
        long comMotorista = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00",
                "kmInicialCarregado", 0, "motoristaId", motorista.id()));
        assertEquals(motorista.id(), buscarViagem(dono, comMotorista).get("motoristaId").asLong());
        // O motorista vinculado vê a viagem aberta pelo dono.
        chamar(GET(VIAGENS + "/" + comMotorista), motorista.token(), null, 200);
        finalizar(dono, comMotorista, corpo("dataFim", "2025-06-02", "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50"), 200);

        long semMotorista = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-03",
                "kmInicialCarregado", 0));
        assertTrue(buscarViagem(dono, semMotorista).get("motoristaId").isNull());

        // O próprio dono como responsável.
        long donoId = usuarios.findByEmail(emailDono).orElseThrow().getId();
        chamar(PUT(VIAGENS + "/" + semMotorista), dono, corpo("dataInicio", "2025-06-03",
                "kmInicialCarregado", 0, "motoristaId", donoId), 200);
        assertEquals(donoId, buscarViagem(dono, semMotorista).get("motoristaId").asLong());
    }

    @Test
    void donoNaoIndicaMotoristaSemVinculoInativoOuDeOutraEmpresa() throws Exception {
        Motorista semVinculo = criarMotorista(dono);
        erro(chamar(POST(VIAGENS), dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "kmInicialCarregado", 0, "motoristaId", semVinculo.id()), 409), "vinculado");

        Motorista inativo = criarMotorista(dono, caminhaoId);
        chamar(PUT("/api/motoristas/" + inativo.id()), dono, corpo("nome", "Inativo", "ativo", false), 200);
        erro(chamar(POST(VIAGENS), dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "kmInicialCarregado", 0, "motoristaId", inativo.id()), 409), "ativo");

        String outroDono = registrar().token();
        Motorista deOutraEmpresa = criarMotorista(outroDono, criarCaminhao(outroDono));
        chamar(POST(VIAGENS), dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "kmInicialCarregado", 0, "motoristaId", deOutraEmpresa.id()), 404);

        assertEquals(0, chamar(GET(VIAGENS), dono, null, 200).size());
    }

    @Test
    void viagemHistoricaAceitaMotoristaQueJaNaoEstaVinculado() throws Exception {
        Motorista antigo = criarMotorista(dono);
        JsonNode v = chamar(POST(VIAGENS + "/historicas"), dono, corpo("caminhaoId", caminhaoId,
                "dataInicio", "2025-05-01", "dataFim", "2025-05-02", "origem", "A", "destino", "B",
                "valorFrete", "1000.00", "kmInicialCarregado", 0, "kmFinalCarregado", 500,
                "consumoMedioCarregado", "2.50", "motoristaId", antigo.id()), 201);
        assertEquals(antigo.id(), v.get("motoristaId").asLong());
    }
}
