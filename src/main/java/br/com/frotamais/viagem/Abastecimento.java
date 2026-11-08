package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
 * Abastecimento de diesel ou Arla lançado em uma viagem.
 *
 * Não tem empresa_id: o isolamento vem da viagem, e toda consulta filtra por
 * viagem.empresa.id.
 */
@Entity
@Table(name = "abastecimentos")
public class Abastecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viagem_id", nullable = false)
    private Viagem viagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoCombustivel tipo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal litros;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    public Long getId() { return id; }
    public Viagem getViagem() { return viagem; }
    public void setViagem(Viagem viagem) { this.viagem = viagem; }
    public TipoCombustivel getTipo() { return tipo; }
    public void setTipo(TipoCombustivel tipo) { this.tipo = tipo; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public BigDecimal getLitros() { return litros; }
    public void setLitros(BigDecimal litros) { this.litros = litros; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
}
