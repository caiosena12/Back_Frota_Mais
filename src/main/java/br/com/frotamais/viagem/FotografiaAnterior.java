package br.com.frotamais.viagem;

import java.time.Instant;

import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Versão da fotografia financeira que uma correção substituiu, guardada como
 * estava (JSON da RentabilidadeResponse daquele momento), junto com a que a
 * substituiu (resultadoNovo; nulo nos registros anteriores à V11). Imutável:
 * gravada numa só inclusão, depois do recálculo.
 */
@Entity
@Table(name = "viagem_fotografias_anteriores")
public class FotografiaAnterior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viagem_id", nullable = false)
    private Viagem viagem;

    @Column(name = "substituida_em", nullable = false)
    private Instant substituidaEm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "substituida_por_id", nullable = false)
    private Usuario substituidaPor;

    /** Perfil de quem fez a correção, no momento dela. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false, columnDefinition = "text")
    private String resultado;

    @Column(name = "resultado_novo", columnDefinition = "text")
    private String resultadoNovo;

    protected FotografiaAnterior() {
    }

    FotografiaAnterior(Viagem viagem, Instant substituidaEm, Usuario substituidaPor, Perfil perfil,
            String resultado, String resultadoNovo) {
        this.viagem = viagem;
        this.substituidaEm = substituidaEm;
        this.substituidaPor = substituidaPor;
        this.perfil = perfil;
        this.resultado = resultado;
        this.resultadoNovo = resultadoNovo;
    }

    public Long getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public Instant getSubstituidaEm() { return substituidaEm; }
    public Usuario getSubstituidaPor() { return substituidaPor; }
    public Perfil getPerfil() { return perfil; }
    public String getResultado() { return resultado; }
    public String getResultadoNovo() { return resultadoNovo; }
}
