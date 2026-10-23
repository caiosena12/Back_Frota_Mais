package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaminhaoService {

    private final CaminhaoRepository repository;

    public CaminhaoService(CaminhaoRepository repository) {
        this.repository = repository;
    }

    public List<CaminhaoResponse> listar() {
        return repository.findAll().stream()
                .map(CaminhaoResponse::de)
                .toList();
    }

    public CaminhaoResponse buscar(Long id) {
        return CaminhaoResponse.de(encontrar(id));
    }

    public CaminhaoResponse criar(CaminhaoRequest request) {
        String placa = normalizarPlaca(request.placa());
        if (repository.existsByPlaca(placa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um caminhão com essa placa");
        }
        Caminhao caminhao = new Caminhao();
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    public CaminhaoResponse atualizar(Long id, CaminhaoRequest request) {
        Caminhao caminhao = encontrar(id);
        String placa = normalizarPlaca(request.placa());
        if (!placa.equals(caminhao.getPlaca()) && repository.existsByPlaca(placa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um caminhão com essa placa");
        }
        preencher(caminhao, request, placa);
        return CaminhaoResponse.de(repository.save(caminhao));
    }

    public void excluir(Long id) {
        repository.delete(encontrar(id));
    }

    private Caminhao encontrar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caminhão não encontrado"));
    }

    private void preencher(Caminhao caminhao, CaminhaoRequest request, String placa) {
        caminhao.setPlaca(placa);
        caminhao.setModelo(request.modelo().trim());
        caminhao.setApelido(request.apelido());
        caminhao.setCapacidadeTanqueLitros(request.capacidadeTanqueLitros());
    }

    private String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase();
    }
}
