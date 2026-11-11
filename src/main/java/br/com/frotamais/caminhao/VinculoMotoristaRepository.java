package br.com.frotamais.caminhao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda consulta filtra pela empresa do caminhão. */
public interface VinculoMotoristaRepository extends JpaRepository<VinculoMotorista, Long> {

    /** Caminhões não excluídos vinculados ao motorista. */
    @Query("""
            select c from VinculoMotorista v join v.caminhao c
            where v.motorista.id = :motoristaId
              and c.empresa.id = :empresaId
              and c.status <> br.com.frotamais.caminhao.StatusCaminhao.EXCLUIDO
            order by c.placa, c.id
            """)
    List<Caminhao> caminhoesDoMotorista(@Param("empresaId") Long empresaId, @Param("motoristaId") Long motoristaId);

    /** Ids dos caminhões vinculados (inclusive excluídos), para exibir o cadastro do motorista. */
    @Query("""
            select v.caminhao.id from VinculoMotorista v
            where v.motorista.id = :motoristaId and v.caminhao.empresa.id = :empresaId
            order by v.caminhao.id
            """)
    List<Long> idsDosCaminhoes(@Param("empresaId") Long empresaId, @Param("motoristaId") Long motoristaId);

    boolean existsByMotoristaIdAndCaminhaoIdAndCaminhaoEmpresaId(Long motoristaId, Long caminhaoId, Long empresaId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from VinculoMotorista v where v.motorista.id = :motoristaId and v.caminhao.empresa.id = :empresaId")
    void removerTodosDoMotorista(@Param("empresaId") Long empresaId, @Param("motoristaId") Long motoristaId);
}
