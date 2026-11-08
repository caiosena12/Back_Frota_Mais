package br.com.frotamais.viagem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por viagem.empresa.id, além do id da viagem. */
public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(
            Long viagemId, Long empresaId);

    Optional<Despesa> findByIdAndViagemIdAndViagemEmpresaIdAndTipo(
            Long id, Long viagemId, Long empresaId, TipoDespesa tipo);

    @Query("""
            select new br.com.frotamais.viagem.TotalPorTipo(v.id, d.tipo, sum(d.valor))
            from Despesa d join d.viagem v
            where v.empresa.id = :empresaId and v.id in :viagemIds
            group by v.id, d.tipo
            """)
    List<TotalPorTipo> totaisPorViagem(
            @Param("empresaId") Long empresaId,
            @Param("viagemIds") Collection<Long> viagemIds);
}
