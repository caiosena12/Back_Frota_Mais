package br.com.frotamais.convite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ConviteRepository extends JpaRepository<Convite, Long> {

    /** Bloqueia o convite: duas ativações simultâneas com o mesmo link não passam as duas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Convite c join fetch c.usuario where c.tokenHash = :hash")
    Optional<Convite> travarPorHash(@Param("hash") String hash);

    /** Convites ainda utilizáveis do usuário (para invalidar num reenvio). */
    @Query("""
            select c from Convite c
            where c.usuario.id = :usuarioId and c.empresa.id = :empresaId
              and c.usadoEm is null and c.invalidadoEm is null and c.expiraEm > :agora
            """)
    List<Convite> pendentes(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId,
            @Param("agora") Instant agora);

    /**
     * Mesmos convites de pendentes(...), bloqueados: um cancelamento e uma
     * ativação simultâneos com o mesmo link não passam os dois. Quem chega
     * depois enxerga o convite já usado ou já invalidado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Convite c
            where c.usuario.id = :usuarioId and c.empresa.id = :empresaId
              and c.usadoEm is null and c.invalidadoEm is null and c.expiraEm > :agora
            """)
    List<Convite> travarPendentes(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId,
            @Param("agora") Instant agora);

    Optional<Convite> findFirstByUsuarioIdAndEmpresaIdOrderByCriadoEmDescIdDesc(Long usuarioId, Long empresaId);
}
