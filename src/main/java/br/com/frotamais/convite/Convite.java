package br.com.frotamais.convite;

import java.time.Instant;

import br.com.frotamais.empresa.Empresa;
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
 * Convite de ativação enviado por e-mail. Guarda só o hash do token: quem lê
 * o banco não consegue ativar a conta de ninguém.
 */
@Entity
@Table(name = "convites")
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "invalidado_em")
    private Instant invalidadoEm;

    protected Convite() {
    }

    Convite(Usuario usuario, Empresa empresa, String tokenHash, Usuario criadoPor, Instant criadoEm, Instant expiraEm) {
        this.usuario = usuario;
        this.empresa = empresa;
        this.tokenHash = tokenHash;
        this.criadoPor = criadoPor;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    /** Ainda aceita ativação: não usado, não invalidado e não expirado. */
    boolean valido(Instant agora) {
        return usadoEm == null && invalidadoEm == null && agora.isBefore(expiraEm);
    }

    void marcarUsado(Instant em) { this.usadoEm = em; }
    void invalidar(Instant em) { this.invalidadoEm = em; }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getUsadoEm() { return usadoEm; }
    public Instant getInvalidadoEm() { return invalidadoEm; }
}
