package br.com.frotamais.viagem;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Toda consulta recebe o empresaId do usuário autenticado. Não use os métodos
 * herdados findById, findAll ou deleteById: eles não filtram por empresa.
 */
public interface ViagemRepository extends JpaRepository<Viagem, Long> {

    @EntityGraph(attributePaths = "caminhao")
    Optional<Viagem> findByIdAndEmpresaId(Long id, Long empresaId);

    /** Busca com bloqueio de escrita, para alterações concorrentes não se sobreporem. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Viagem v where v.id = :id and v.empresa.id = :empresaId")
    Optional<Viagem> travarPorIdEEmpresa(@Param("id") Long id, @Param("empresaId") Long empresaId);

    boolean existsByEmpresaIdAndCaminhaoIdAndStatus(Long empresaId, Long caminhaoId, StatusViagem status);

    @Query("""
            select v from Viagem v join fetch v.caminhao c
            where v.empresa.id = :empresaId
              and (:status is null or v.status = :status)
              and (:caminhaoId is null or c.id = :caminhaoId)
            order by v.dataInicio desc, v.id desc
            """)
    List<Viagem> listar(
            @Param("empresaId") Long empresaId,
            @Param("status") StatusViagem status,
            @Param("caminhaoId") Long caminhaoId);

    @Query("""
            select v from Viagem v join fetch v.caminhao c
            where v.empresa.id = :empresaId
              and v.status = br.com.frotamais.viagem.StatusViagem.FINALIZADA
              and (cast(:de as date) is null or v.dataFim >= :de)
              and (cast(:ate as date) is null or v.dataFim <= :ate)
              and (:caminhaoId is null or c.id = :caminhaoId)
            order by v.dataFim, v.id
            """)
    List<Viagem> finalizadasNoPeriodo(
            @Param("empresaId") Long empresaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate,
            @Param("caminhaoId") Long caminhaoId);
}
