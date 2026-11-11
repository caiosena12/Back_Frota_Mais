package br.com.frotamais.caminhao;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.viagem.StatusViagem;
import br.com.frotamais.viagem.ViagemRepository;

/**
 * Cadastro de caminhões da empresa do usuário logado.
 *
 * Exclusão lógica: o caminhão excluído sai da lista operacional e não entra
 * em novas viagens, mas continua no banco com todo o histórico (viagens,
 * lançamentos, resultados). O DONO ainda o consulta pelo id ou listando com
 * incluirExcluidos=true.
 */
@Service
@Transactional
public class CaminhaoService {

    private final CaminhaoRepository repository;
    private final EmpresaRepository empresas;
    private final UsuarioLogado logado;
    private final ViagemRepository viagens;
    private final AcessoCaminhao acesso;

    public CaminhaoService(CaminhaoRepository repository, EmpresaRepository empresas, UsuarioLogado logado,
            ViagemRepository viagens, AcessoCaminhao acesso) {
        this.repository = repository;
        this.empresas = empresas;
        this.logado = logado;
        this.viagens = viagens;
        this.acesso = acesso;
    }

    /** DONO: caminhões da empresa (excluídos só se pedidos). MOTORISTA: só os vinculados a ele, não excluídos. */
    @Transactional(readOnly = true)
    public List<CaminhaoResponse> listar(boolean incluirExcluidos) {
        return acesso.listarVisiveis(incluirExcluidos).stream()
                .map(CaminhaoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaminhaoResponse buscar(Long id) {
        return CaminhaoResponse.de(acesso.visivel(id));
    }

    public CaminhaoResponse criar(CaminhaoRequest request) {
        Long empresaId = logado.empresaId();
        String placa = normalizarPlaca(request.placa());
        StatusCaminhao status = request.status() != null ? request.status() : StatusCaminhao.ATIVO;
        exigirStatusOperacional(status);
        if (repository.existePlacaOperacional(empresaId, placa, null)) {
            throw RegraNegocioException.conflito("Já existe um caminhão com essa placa.");
        }
        Caminhao caminhao = new Caminhao();
        caminhao.setEmpresa(empresas.getReferenceById(empresaId));
        preencher(caminhao, request, placa);
        caminhao.setStatus(status);
        if (status == StatusCaminhao.INATIVO) {
            caminhao.setInativadoEm(Datas.agora());
        }
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    /**
     * PUT = substituição integral, com status obrigatório. Bloqueia o caminhão
     * (o mesmo bloqueio da abertura de viagem), para que a inativação e a
     * abertura de uma viagem não se cruzem.
     */
    public CaminhaoResponse atualizar(Long id, CaminhaoRequest request) {
        Caminhao caminhao = travar(id);
        if (caminhao.isExcluido()) {
            throw RegraNegocioException.conflito("Caminhão excluído não pode ser alterado.");
        }
        if (request.status() == null) {
            throw RegraNegocioException.invalido("Informe o status do caminhão: ATIVO ou INATIVO.");
        }
        StatusCaminhao novoStatus = request.status();
        exigirStatusOperacional(novoStatus);
        if (novoStatus == StatusCaminhao.INATIVO && caminhao.getStatus() != StatusCaminhao.INATIVO) {
            exigirSemViagemEmAndamento(caminhao, "inativado");
        }
        String placa = normalizarPlaca(request.placa());
        if (repository.existePlacaOperacional(logado.empresaId(), placa, caminhao.getId())) {
            throw RegraNegocioException.conflito("Já existe um caminhão com essa placa.");
        }
        preencher(caminhao, request, placa);
        if (novoStatus == StatusCaminhao.INATIVO && caminhao.getStatus() != StatusCaminhao.INATIVO) {
            caminhao.setInativadoEm(Datas.agora());
        } else if (novoStatus == StatusCaminhao.ATIVO) {
            caminhao.setInativadoEm(null);
        }
        caminhao.setStatus(novoStatus);
        return CaminhaoResponse.de(caminhao);
    }

    /**
     * Exclusão lógica. Recusada com viagem em andamento (finalize ou cancele
     * antes). Nada é apagado; uma segunda exclusão responde 409.
     */
    public void excluir(Long id) {
        Caminhao caminhao = travar(id);
        if (caminhao.isExcluido()) {
            throw RegraNegocioException.conflito("O caminhão já está excluído.");
        }
        exigirSemViagemEmAndamento(caminhao, "excluído");
        caminhao.setStatus(StatusCaminhao.EXCLUIDO);
        caminhao.setExcluidoEm(Datas.agora());
    }

    private void exigirSemViagemEmAndamento(Caminhao caminhao, String acao) {
        if (viagens.existsByEmpresaIdAndCaminhaoIdAndStatus(
                logado.empresaId(), caminhao.getId(), StatusViagem.EM_ANDAMENTO)) {
            throw RegraNegocioException.conflito("O caminhão tem uma viagem em andamento e não pode ser "
                    + acao + ". Finalize a viagem antes.");
        }
    }

    private static void exigirStatusOperacional(StatusCaminhao status) {
        if (status == StatusCaminhao.EXCLUIDO) {
            throw RegraNegocioException.invalido(
                    "Para excluir o caminhão use a exclusão (DELETE); o status aceita ATIVO ou INATIVO.");
        }
    }

    private Caminhao travar(Long id) {
        return repository.travarPorIdEEmpresa(id, logado.empresaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Caminhão não encontrado."));
    }

    private static void preencher(Caminhao caminhao, CaminhaoRequest request, String placa) {
        caminhao.setPlaca(placa);
        caminhao.setMarca(request.marca().trim());
        caminhao.setModelo(request.modelo().trim());
        caminhao.setAno(request.ano());
        caminhao.setEixos(request.eixos());
        caminhao.setCapacidadeTanqueLitros(request.capacidadeTanqueLitros());
    }

    /** Maiúsculas, sem espaço, hífen, ponto ou outro separador. */
    static String normalizarPlaca(String placa) {
        String normalizada = placa.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalizada.isEmpty() || normalizada.length() > 8) {
            throw RegraNegocioException.invalido("Placa inválida: use letras e números, até 8 caracteres.");
        }
        return normalizada;
    }
}
