package br.com.frotamais.caminhao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CaminhaoRepository extends JpaRepository<Caminhao, Long> {

    List<Caminhao> findAllByEmpresaId(Long empresaId);

    Optional<Caminhao> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndPlaca(Long empresaId, String placa);
}
