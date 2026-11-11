package br.com.frotamais.viagem;

import java.time.Instant;

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
 * Versão da fotografia financeira que uma correção substituiu, guardada como
 * estava (JSON da RentabilidadeResponse daquele momento). Imutável.
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

    @Column(nullable = false, columnDefinition = "text")
    private String resultado;

    protected FotografiaAnterior() {
    }

    FotografiaAnterior(Viagem viagem, Instant substituidaEm, Usuario substituidaPor, String resultado) {
        this.viagem = viagem;
        this.substituidaEm = substituidaEm;
        this.substituidaPor = substituidaPor;
        this.resultado = resultado;
    }

    public Long getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public Instant getSubstituidaEm() { return substituidaEm; }
    public Usuario getSubstituidaPor() { return substituidaPor; }
    public String getResultado() { return resultado; }
}
