package br.com.frotamais.viagem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas sempre pela viagem e pela empresa dela. Não use os métodos herdados sem filtro. */
public interface FotografiaAnteriorRepository extends JpaRepository<FotografiaAnterior, Long> {

    @Query("""
            select f from FotografiaAnterior f join fetch f.substituidaPor
            where f.viagem.id = :viagemId and f.viagem.empresa.id = :empresaId
            order by f.substituidaEm, f.id
            """)
    List<FotografiaAnterior> daViagem(@Param("viagemId") Long viagemId, @Param("empresaId") Long empresaId);
}
