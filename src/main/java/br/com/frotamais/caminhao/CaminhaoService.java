package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;

@Service
public class CaminhaoService {

    private final CaminhaoRepository repository;
    private final EmpresaRepository empresas;
    private final UsuarioLogado logado;

    public CaminhaoService(CaminhaoRepository repository, EmpresaRepository empresas, UsuarioLogado logado) {
        this.repository = repository;
        this.empresas = empresas;
        this.logado = logado;
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um caminhão com essa placa");
        }
        Caminhao caminhao = new Caminhao();
        caminhao.setEmpresa(empresas.getReferenceById(empresaId));
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    public CaminhaoResponse atualizar(Long id, CaminhaoRequest request) {
        Caminhao caminhao = encontrar(id);
        String placa = normalizarPlaca(request.placa());
        if (!placa.equals(caminhao.getPlaca())
                && repository.existsByEmpresaIdAndPlaca(logado.empresaId(), placa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um caminhão com essa placa");
        }
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    public void excluir(Long id) {
        repository.delete(encontrar(id));
    }

    private Caminhao encontrar(Long id) {
        return repository.findByIdAndEmpresaId(id, logado.empresaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caminhão não encontrado"));
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
