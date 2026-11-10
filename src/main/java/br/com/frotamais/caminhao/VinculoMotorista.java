package br.com.frotamais.caminhao;

import java.time.Instant;

import br.com.frotamais.comum.Datas;
import br.com.frotamais.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Acesso de um MOTORISTA a um caminhão. Sem empresa_id próprio: motorista e
 * caminhão pertencem à mesma empresa, e toda consulta filtra por
 * caminhao.empresa.id.
 */
@Entity
@Table(name = "motorista_caminhoes")
public class VinculoMotorista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motorista_id", nullable = false)
    private Usuario motorista;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caminhao_id", nullable = false)
    private Caminhao caminhao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Datas.agora();

    protected VinculoMotorista() {
    }

    public VinculoMotorista(Usuario motorista, Caminhao caminhao, Usuario criadoPor) {
        this.motorista = motorista;
        this.caminhao = caminhao;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public Usuario getMotorista() { return motorista; }
    public Caminhao getCaminhao() { return caminhao; }
    public Instant getCriadoEm() { return criadoEm; }
}
