package br.com.frotamais.caminhao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CaminhaoRepository extends JpaRepository<Caminhao, Long> {

    List<Caminhao> findAllByEmpresaId(Long empresaId);

    Optional<Caminhao> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndPlaca(Long empresaId, String placa);

    /**
     * Busca com bloqueio de escrita. Usado ao abrir uma viagem, para que duas
     * requisições simultâneas não abram duas viagens no mesmo caminhão.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caminhao c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Caminhao> travarPorIdEEmpresa(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
