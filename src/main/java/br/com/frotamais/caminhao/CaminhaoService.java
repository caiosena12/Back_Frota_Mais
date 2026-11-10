package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.viagem.StatusViagem;
import br.com.frotamais.viagem.ViagemRepository;

@Service
public class CaminhaoService {

    private final CaminhaoRepository repository;
    private final EmpresaRepository empresas;
    private final UsuarioLogado logado;
    private final ViagemRepository viagens;

    public CaminhaoService(CaminhaoRepository repository, EmpresaRepository empresas, UsuarioLogado logado,
            ViagemRepository viagens) {
        this.repository = repository;
        this.empresas = empresas;
        this.logado = logado;
        this.viagens = viagens;
    }

    public List<CaminhaoResponse> listar() {
        return repository.findAllByEmpresaId(logado.empresaId()).stream()
                .map(CaminhaoResponse::de)
                .toList();
    }

    public CaminhaoResponse buscar(Long id) {
        return CaminhaoResponse.de(encontrar(id));
    }

    public CaminhaoResponse criar(CaminhaoRequest request) {
        Long empresaId = logado.empresaId();
        String placa = normalizarPlaca(request.placa());
        if (repository.existsByEmpresaIdAndPlaca(empresaId, placa)) {
            throw RegraNegocioException.conflito("Já existe um caminhão com essa placa.");
        }
        Caminhao caminhao = new Caminhao();
        caminhao.setEmpresa(empresas.getReferenceById(empresaId));
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    /**
     * Bloqueia o caminhão (o mesmo bloqueio da abertura de viagem), para que a
     * inativação e a abertura de uma viagem não se cruzem.
     */
    @Transactional
    public CaminhaoResponse atualizar(Long id, CaminhaoRequest request) {
        Caminhao caminhao = travar(id);
        StatusCaminhao novoStatus = request.status() != null ? request.status() : StatusCaminhao.ATIVO;
        if (novoStatus == StatusCaminhao.INATIVO
                && viagens.existsByEmpresaIdAndCaminhaoIdAndStatus(
                        logado.empresaId(), caminhao.getId(), StatusViagem.EM_ANDAMENTO)) {
            throw RegraNegocioException.conflito(
                    "O caminhão tem uma viagem em andamento e não pode ser inativado. Finalize a viagem antes.");
        }
        String placa = normalizarPlaca(request.placa());
        if (!placa.equals(caminhao.getPlaca())
                && repository.existsByEmpresaIdAndPlaca(logado.empresaId(), placa)) {
            throw RegraNegocioException.conflito("Já existe um caminhão com essa placa.");
        }
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    /**
     * Caminhão com qualquer viagem (em andamento ou finalizada) não é excluído:
     * o histórico financeiro depende dele. Não há exclusão em cascata. Se uma
     * viagem for criada entre a checagem e a exclusão, a chave estrangeira do
     * banco recusa e a resposta também é 409.
     */
    @Transactional
    public void excluir(Long id) {
        Caminhao caminhao = travar(id);
        if (viagens.existsByEmpresaIdAndCaminhaoId(logado.empresaId(), caminhao.getId())) {
            throw RegraNegocioException.conflito(
                    "O caminhão tem histórico de viagens e não pode ser excluído. Inative-o.");
        }
        try {
            repository.delete(caminhao);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw RegraNegocioException.conflito(
                    "O caminhão tem histórico de viagens e não pode ser excluído. Inative-o.");
        }
    }

    private Caminhao travar(Long id) {
        return repository.travarPorIdEEmpresa(id, logado.empresaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Caminhão não encontrado."));
    }

    private Caminhao encontrar(Long id) {
        return repository.findByIdAndEmpresaId(id, logado.empresaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Caminhão não encontrado."));
    }

    private void preencher(Caminhao caminhao, CaminhaoRequest request, String placa) {
        caminhao.setPlaca(placa);
        caminhao.setModelo(request.modelo().trim());
        caminhao.setAno(request.ano());
        caminhao.setStatus(request.status() != null ? request.status() : StatusCaminhao.ATIVO);
        caminhao.setCapacidadeTanqueLitros(request.capacidadeTanqueLitros());
    }

    private String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase();
    }
}
