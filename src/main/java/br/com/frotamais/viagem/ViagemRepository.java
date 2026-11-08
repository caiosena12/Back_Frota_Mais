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

    boolean existsByEmpresaIdAndCaminhaoId(Long empresaId, Long caminhaoId);

    /** Caminhão de uma viagem da empresa, sem carregar a entidade (usado antes de bloquear o caminhão). */
    @Query("select v.caminhao.id from Viagem v where v.id = :id and v.empresa.id = :empresaId")
    Optional<Long> caminhaoDaViagem(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /**
     * Existe outra viagem do mesmo caminhão em conflito com o período [inicio, fim]?
     * Há conflito se as duas começam no mesmo dia, ou se uma começa antes do
     * fim da outra e termina depois do início dela. Assim, a única data que
     * duas viagens podem compartilhar é o dia em que uma termina e a outra
     * começa (descarga do frete e saída vazia para o próximo).
     * fim nulo = período aberto (viagem em andamento), sem limite superior;
     * o mesmo vale para as viagens existentes com dataFim nula.
     */
    @Query("""
            select count(v) > 0 from Viagem v
            where v.empresa.id = :empresaId
              and v.caminhao.id = :caminhaoId
              and (:ignorarId is null or v.id <> :ignorarId)
              and (v.dataInicio = :inicio
                   or ((cast(:fim as date) is null or v.dataInicio < :fim)
                       and (v.dataFim is null or v.dataFim > :inicio)))
            """)
    boolean existeSobreposicao(
            @Param("empresaId") Long empresaId,
            @Param("caminhaoId") Long caminhaoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("ignorarId") Long ignorarId);

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
