package br.com.frotamais.viagem;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra por viagem.empresa.id, além do id da viagem. */
public interface AbastecimentoRepository extends JpaRepository<Abastecimento, Long> {

    List<Abastecimento> findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(
            Long viagemId, Long empresaId);

    Optional<Abastecimento> findByIdAndViagemIdAndViagemEmpresaId(
            Long id, Long viagemId, Long empresaId);

    /** Totais de diesel por viagem dos caminhões informados, para o preço médio. */
    /** Algum abastecimento da viagem com data anterior a data? */
    boolean existsByViagemIdAndViagemEmpresaIdAndDataBefore(Long viagemId, Long empresaId, LocalDate data);

    @Query("""
            select new br.com.frotamais.viagem.TotaisDiesel(
                v.id, v.caminhao.id, v.dataInicio, sum(a.litros), sum(a.valorTotal))
            from Abastecimento a join a.viagem v
            where v.empresa.id = :empresaId
              and v.caminhao.id in :caminhaoIds
              and a.tipo = br.com.frotamais.viagem.TipoCombustivel.DIESEL
            group by v.id, v.caminhao.id, v.dataInicio
            """)
    List<TotaisDiesel> totaisDieselPorViagem(
            @Param("empresaId") Long empresaId,
            @Param("caminhaoIds") Collection<Long> caminhaoIds);

    @Query("""
            select new br.com.frotamais.viagem.TotalPorTipo(v.id, a.tipo, sum(a.valorTotal))
            from Abastecimento a join a.viagem v
            where v.empresa.id = :empresaId and v.id in :viagemIds
            group by v.id, a.tipo
            """)
    List<TotalPorTipo> totaisPorViagem(
            @Param("empresaId") Long empresaId,
            @Param("viagemIds") Collection<Long> viagemIds);
}
