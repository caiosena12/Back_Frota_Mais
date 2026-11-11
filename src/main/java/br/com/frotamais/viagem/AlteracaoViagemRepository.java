package br.com.frotamais.viagem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Consultas sempre pela viagem e pela empresa dela. Não use os métodos herdados sem filtro. */
public interface AlteracaoViagemRepository extends JpaRepository<AlteracaoViagem, Long> {

    @Query("""
            select a from AlteracaoViagem a join fetch a.usuario
            where a.viagem.id = :viagemId and a.viagem.empresa.id = :empresaId
            order by a.alteradaEm, a.id
            """)
    List<AlteracaoViagem> daViagem(@Param("viagemId") Long viagemId, @Param("empresaId") Long empresaId);
}
