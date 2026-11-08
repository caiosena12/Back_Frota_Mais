package br.com.frotamais.viagem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

/**
 * Perfis com o filtro real. Enquanto não existir o vínculo motorista–caminhão
 * (Anexo A, P12), o MOTORISTA não acessa viagens: liberá-lo daria acesso a
 * todos os caminhões da empresa. O perfil vem do banco, não do token.
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

    @Test
    void motoristaNaoAcessaViagensEnquantoNaoHouverVinculoComCaminhao() throws Exception {
        Conta conta = registrar();
        String token = conta.token();
        long caminhaoId = criarCaminhao(token);
        long viagem = criarViagem(token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "origem", "A", "destino", "B", "valorFrete", "1000.00", "kmInicialCarregado", 0));
        long pedagio = pedagio(token, viagem, "2025-06-01", "10.00").get("id").asLong();

        // O mesmo token, que diz DONO, passa a pertencer a um MOTORISTA no banco.
        Usuario usuario = usuarios.findByEmail(conta.email()).orElseThrow();
        usuario.setPerfil(Perfil.MOTORISTA);
        usuarios.saveAndFlush(usuario);

        String url = VIAGENS + "/" + viagem;
        chamar(GET(VIAGENS), token, null, 403);
        chamar(GET(url), token, null, 403);
        chamar(GET(VIAGENS + "/indicadores"), token, null, 403);
        chamar(POST(VIAGENS), token, corpo("caminhaoId", caminhaoId, "dataInicio", "2025-06-01",
                "kmInicialVazio", 0), 403);
        chamar(PUT(url), token, corpo("dataInicio", "2025-06-01", "kmInicialCarregado", 0), 403);
        finalizar(token, viagem, corpo("dataFim", "2025-06-01", "kmFinalCarregado", 10,
                "consumoMedioCarregado", "2.50"), 403);
        chamar(POST(url + "/abastecimentos"), token, corpo("tipo", "DIESEL", "data", "2025-06-01",
                "litros", "1", "valorTotal", "1.00"), 403);
        chamar(POST(url + "/pedagios"), token, corpo("data", "2025-06-01", "valor", "1.00"), 403);
        chamar(PUT(url + "/pedagios/" + pedagio), token, corpo("data", "2025-06-01", "valor", "1.00"), 403);
        chamar(DELETE(url + "/pedagios/" + pedagio), token, null, 403);
        chamar(POST(url + "/despesas-extras"), token, corpo("descricao", "x", "data", "2025-06-01",
                "valor", "1.00"), 403);

        // De volta a DONO, o acesso retorna e nada foi alterado.
        usuario.setPerfil(Perfil.DONO);
        usuarios.saveAndFlush(usuario);
        var v = buscarViagem(token, viagem);
        org.junit.jupiter.api.Assertions.assertEquals(1, v.get("pedagios").size());
        dinheiro("10.00", v.get("pedagios").get(0).get("valor"));
    }
}
