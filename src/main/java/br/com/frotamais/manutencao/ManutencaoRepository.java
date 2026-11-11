package br.com.frotamais.manutencao;

import java.math.BigDecimal;
import java.time.LocalDate;
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
public interface ManutencaoRepository extends JpaRepository<Manutencao, Long> {

    @Query("select m from Manutencao m join fetch m.caminhao where m.id = :id and m.empresa.id = :empresaId")
    Optional<Manutencao> buscar(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Manutencao m where m.id = :id and m.empresa.id = :empresaId")
    Optional<Manutencao> travar(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("""
            select m from Manutencao m join fetch m.caminhao c
            where m.empresa.id = :empresaId
              and (:incluirExcluidas = true or m.excluidaEm is null)
              and (:caminhaoId is null or c.id = :caminhaoId)
              and (cast(:de as date) is null or m.data >= :de)
              and (cast(:ate as date) is null or m.data <= :ate)
            order by m.data desc, m.id desc
            """)
    List<Manutencao> listar(
            @Param("empresaId") Long empresaId,
            @Param("caminhaoId") Long caminhaoId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate,
            @Param("incluirExcluidas") boolean incluirExcluidas);

    /** Soma das manutenções não excluídas no período (pela data), da empresa ou de um caminhão. */
    @Query("""
            select coalesce(sum(m.valor), 0) from Manutencao m
            where m.empresa.id = :empresaId
              and m.excluidaEm is null
              and (:caminhaoId is null or m.caminhao.id = :caminhaoId)
              and (cast(:de as date) is null or m.data >= :de)
              and (cast(:ate as date) is null or m.data <= :ate)
            """)
    BigDecimal total(
            @Param("empresaId") Long empresaId,
            @Param("caminhaoId") Long caminhaoId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);

    /** Quantidade de manutenções não excluídas no período. */
    @Query("""
            select count(m) from Manutencao m
            where m.empresa.id = :empresaId
              and m.excluidaEm is null
              and (:caminhaoId is null or m.caminhao.id = :caminhaoId)
              and (cast(:de as date) is null or m.data >= :de)
              and (cast(:ate as date) is null or m.data <= :ate)
            """)
    long quantidade(
            @Param("empresaId") Long empresaId,
            @Param("caminhaoId") Long caminhaoId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate);
}
