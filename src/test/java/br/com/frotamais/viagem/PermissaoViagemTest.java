package br.com.frotamais.viagem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.JsonNode;

/**
 * Perfis com o filtro real. O MOTORISTA só alcança viagens de caminhões
 * vinculados a ele; sem vínculo, não vê nem altera nada (as viagens respondem
 * 404, como as de outra empresa). Rotas exclusivas do DONO respondem 403.
 * O perfil vem do banco, não do token.
 */
class PermissaoViagemTest extends ApiViagemTeste {

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void semTokenResponde401() throws Exception {
        chamar(GET(VIAGENS), null, null, 401);
        chamar(GET(VIAGENS + "/1"), null, null, 401);
        chamar(GET(VIAGENS + "/indicadores"), null, null, 401);
        chamar(POST(VIAGENS), null, corpo("caminhaoId", 1, "dataInicio", "2025-06-01", "kmInicialVazio", 0), 401);
        chamar(POST(VIAGENS + "/1/pedagios"), null, corpo("data", "2025-06-01", "valor", "1.00"), 401);
        chamar(GET(VIAGENS), "token-invalido", null, 401);
    }

    /** Tenta tudo sobre uma viagem que o token não pode alcançar; nada pode mudar. */
    private void semAcessoAViagem(String token, long caminhaoId, long viagem, long pedagio) throws Exception {
        String url = VIAGENS + "/" + viagem;
        assertEquals(0, chamar(GET(VIAGENS), token, null, 200).size());
        chamar(GET(url), token, null, 404);
        chamar(GET(VIAGENS + "/indicadores"), token, null, 403);
        chamar(POST(VIAGENS + "/historicas"), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-05-01",
                "dataFim", "2025-05-02", "origem", "A", "destino", "B", "valorFrete", "1.00",
                "kmInicialCarregado", 0, "kmFinalCarregado", 10, "consumoMedioCarregado", "2.50"), 403);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-02",
                "kmInicialVazio", 0), 404);
        chamar(PUT(url), token, corpo("dataInicio", "2025-06-01", "kmInicialCarregado", 0), 404);
        finalizar(token, viagem, corpo("dataFim", "2025-06-01", "kmFinalCarregado", 10,
                "consumoMedioCarregado", "2.50"), 404);
        chamar(POST(url + "/abastecimentos"), token, corpo("tipo", "DIESEL", "data", "2025-06-01",
                "litros", "1", "valorTotal", "1.00"), 404);
        chamar(POST(url + "/pedagios"), token, corpo("data", "2025-06-01", "valor", "1.00"), 404);
        chamar(PUT(url + "/pedagios/" + pedagio), token, corpo("data", "2025-06-01", "valor", "1.00"), 404);
        chamar(DELETE(url + "/pedagios/" + pedagio), token, null, 404);
        chamar(POST(url + "/despesas-extras"), token, corpo("descricao", "x", "data", "2025-06-01",
                "valor", "1.00"), 404);
    }

    private void nadaMudou(String tokenDono, long viagem) throws Exception {
        JsonNode v = buscarViagem(tokenDono, viagem);
        assertEquals("EM_ANDAMENTO", v.get("status").asString());
        assertEquals(1, v.get("pedagios").size());
        dinheiro("10.00", v.get("pedagios").get(0).get("valor"));
        assertEquals(0, v.get("abastecimentosDiesel").size());
        assertEquals(0, v.get("despesasExtras").size());
    }

    @Test
    void motoristaSemVinculoNaoVeNemAlteraViagens() throws Exception {
        String dono = registrar().token();
        long caminhaoId = criarCaminhao(dono);
        long viagem = criarViagem(dono, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        long pedagio = pedagio(dono, viagem, "2025-06-01", "10.00").get("id").asLong();

        Motorista semVinculo = criarMotorista(dono);
        semAcessoAViagem(semVinculo.token(), caminhaoId, viagem, pedagio);
        nadaMudou(dono, viagem);
    }

    @Test
    void perfilVemDoBancoEDonoRebaixadoAMotoristaPerdeOAcesso() throws Exception {
        Conta conta = registrar();
        String token = conta.token();
        long caminhaoId = criarCaminhao(token);
        long viagem = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        long pedagio = pedagio(token, viagem, "2025-06-01", "10.00").get("id").asLong();

        // O mesmo token, que diz DONO, passa a pertencer a um MOTORISTA (sem vínculos) no banco.
        Usuario usuario = usuarios.findByEmail(conta.email()).orElseThrow();
        usuario.setPerfil(Perfil.MOTORISTA);
        usuarios.saveAndFlush(usuario);
        semAcessoAViagem(token, caminhaoId, viagem, pedagio);

        // De volta a DONO, o acesso retorna e nada foi alterado.
        usuario.setPerfil(Perfil.DONO);
        usuarios.saveAndFlush(usuario);
        nadaMudou(token, viagem);
    }
}
