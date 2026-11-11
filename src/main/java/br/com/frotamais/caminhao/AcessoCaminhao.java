package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.stereotype.Component;

import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.seguranca.UsuarioLogado;

/**
 * Quais caminhões o usuário logado enxerga. Ponto único dessa regra, usado
 * pelos caminhões e pelas viagens.
 *
 * DONO: todos os caminhões da empresa. MOTORISTA: só os vinculados a ele e não
 * excluídos (o vínculo motorista–caminhão é criado pelo DONO). Fora disso a
 * resposta é 404, como para recurso de outra empresa: o motorista não fica
 * sabendo que o caminhão existe.
 */
@Component
public class AcessoCaminhao {

    private final CaminhaoRepository caminhoes;
    private final UsuarioLogado logado;
    private final VinculoMotoristaRepository vinculos;

    public AcessoCaminhao(CaminhaoRepository caminhoes, UsuarioLogado logado, VinculoMotoristaRepository vinculos) {
        this.caminhoes = caminhoes;
        this.logado = logado;
        this.vinculos = vinculos;
    }

    public List<Caminhao> listarVisiveis(boolean incluirExcluidos) {
        Long empresaId = logado.empresaId();
        if (logado.isDono()) {
            return incluirExcluidos
                    ? caminhoes.findAllByEmpresaIdOrderByPlacaAscIdAsc(empresaId)
                    : caminhoes.findAllByEmpresaIdAndStatusNotOrderByPlacaAscIdAsc(empresaId, StatusCaminhao.EXCLUIDO);
        }
        return vinculos.caminhoesDoMotorista(empresaId, logado.usuarioId());
    }

    /** Caminhão visível ao usuário logado, ou 404. */
    public Caminhao visivel(Long caminhaoId) {
        Long empresaId = logado.empresaId();
        Caminhao caminhao = caminhoes.findByIdAndEmpresaId(caminhaoId, empresaId).orElseThrow(AcessoCaminhao::naoEncontrado);
        if (!podeVer(caminhao)) {
            throw naoEncontrado();
        }
        return caminhao;
    }

    /** O usuário logado opera este caminhão (já carregado e da mesma empresa)? */
    public boolean podeVer(Caminhao caminhao) {
        if (logado.isDono()) {
            return true;
        }
        return !caminhao.isExcluido()
                && vinculos.existsByMotoristaIdAndCaminhaoIdAndCaminhaoEmpresaId(
                        logado.usuarioId(), caminhao.getId(), logado.empresaId());
    }

    public static RegraNegocioException naoEncontrado() {
        return RegraNegocioException.naoEncontrado("Caminhão não encontrado.");
    }
}
