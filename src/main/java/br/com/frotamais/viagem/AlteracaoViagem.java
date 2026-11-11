package br.com.frotamais.viagem;

import java.time.Instant;

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
 * Um campo alterado numa correção de viagem encerrada: quem, quando, o quê,
 * valor anterior e novo. Imutável depois de gravado (só há getters).
 */
@Entity
@Table(name = "viagem_alteracoes")
public class AlteracaoViagem {

    /** O que foi corrigido: a própria viagem ou um dos lançamentos dela. */
    public enum Entidade { VIAGEM, ABASTECIMENTO, PEDAGIO, DESPESA_EXTRA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viagem_id", nullable = false)
    private Viagem viagem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "alterada_em", nullable = false)
    private Instant alteradaEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Entidade entidade;

    @Column(name = "registro_id", nullable = false)
    private Long registroId;

    @Column(nullable = false, length = 60)
    private String campo;

    @Column(name = "valor_anterior", length = 1000)
    private String valorAnterior;

    @Column(name = "valor_novo", length = 1000)
    private String valorNovo;

    protected AlteracaoViagem() {
    }

    AlteracaoViagem(Viagem viagem, Usuario usuario, Instant alteradaEm, Entidade entidade, Long registroId,
            String campo, String valorAnterior, String valorNovo) {
        this.viagem = viagem;
        this.usuario = usuario;
        this.alteradaEm = alteradaEm;
        this.entidade = entidade;
        this.registroId = registroId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public Long getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public Usuario getUsuario() { return usuario; }
    public Instant getAlteradaEm() { return alteradaEm; }
    public Entidade getEntidade() { return entidade; }
    public Long getRegistroId() { return registroId; }
    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
}
