package br.com.frotamais.caminhao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Toda consulta recebe o empresaId do usuário autenticado. Não use os métodos
 * herdados findById, findAll ou deleteById: eles não filtram por empresa.
 */
public interface CaminhaoRepository extends JpaRepository<Caminhao, Long> {

    List<Caminhao> findAllByEmpresaIdOrderByPlacaAscIdAsc(Long empresaId);

    List<Caminhao> findAllByEmpresaIdAndStatusNotOrderByPlacaAscIdAsc(Long empresaId, StatusCaminhao status);

    List<Caminhao> findAllByEmpresaIdAndIdIn(Long empresaId, java.util.Collection<Long> ids);

    Optional<Caminhao> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Já existe caminhão não excluído da empresa com a placa (comparada sem
     * espaço, hífen ou ponto, também nos cadastros antigos)? ignorarId: o
     * próprio caminhão, na alteração.
     */
    @Query(nativeQuery = true, value = """
            select exists (
                select 1 from caminhoes c
                where c.empresa_id = :empresaId
                  and c.status <> 'EXCLUIDO'
                  and upper(regexp_replace(c.placa, '[^A-Za-z0-9]', '', 'g')) = :placa
                  and (cast(:ignorarId as bigint) is null or c.id <> :ignorarId))
            """)
    boolean existePlacaOperacional(@Param("empresaId") Long empresaId, @Param("placa") String placa,
            @Param("ignorarId") Long ignorarId);

    /**
     * Busca com bloqueio de escrita. Usado ao abrir uma viagem, para que duas
     * requisições simultâneas não abram duas viagens no mesmo caminhão.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caminhao c where c.id = :id and c.empresa.id = :empresaId")
    Optional<Caminhao> travarPorIdEEmpresa(@Param("id") Long id, @Param("empresaId") Long empresaId);
}
