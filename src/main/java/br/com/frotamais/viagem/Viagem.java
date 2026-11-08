package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.empresa.Empresa;
import br.com.frotamais.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Um frete. Começa quando o caminhão sai para buscar a carga (deslocamento
 * vazio, opcional) e termina quando a carga é descarregada.
 *
 * Quilometragem por três leituras do odômetro:
 * kmInicialVazio (saída vazia) → kmInicialCarregado (carregamento, que é
 * também o fim do vazio) → kmFinalCarregado (descarga).
 */
@Entity
@Table(name = "viagens", indexes = {
        @Index(name = "idx_viagens_empresa_caminhao", columnList = "empresa_id, caminhao_id"),
        @Index(name = "idx_viagens_empresa_data_fim", columnList = "empresa_id, data_fim")
})
public class Viagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caminhao_id", nullable = false)
    private Caminhao caminhao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusViagem status = StatusViagem.EM_ANDAMENTO;

    @Column(length = 120)
    private String origem;

    @Column(length = 120)
    private String destino;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "valor_frete", precision = 15, scale = 2)
    private BigDecimal valorFrete;

    @Column(name = "km_inicial_vazio")
    private Integer kmInicialVazio;

    @Column(name = "km_inicial_carregado")
    private Integer kmInicialCarregado;

    @Column(name = "km_final_carregado")
    private Integer kmFinalCarregado;

    @Column(name = "consumo_medio_vazio", precision = 4, scale = 2)
    private BigDecimal consumoMedioVazio;

    @Column(name = "consumo_medio_carregado", precision = 4, scale = 2)
    private BigDecimal consumoMedioCarregado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Instant.now();

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm = Instant.now();

    @Column(name = "finalizada_em")
    private Instant finalizadaEm;

    public boolean isFinalizada() {
        return status == StatusViagem.FINALIZADA;
    }

    public void marcarAlteracao() {
        this.atualizadaEm = Instant.now();
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Caminhao getCaminhao() { return caminhao; }
    public void setCaminhao(Caminhao caminhao) { this.caminhao = caminhao; }
    public StatusViagem getStatus() { return status; }
    public void setStatus(StatusViagem status) { this.status = status; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }
    public BigDecimal getValorFrete() { return valorFrete; }
    public void setValorFrete(BigDecimal valorFrete) { this.valorFrete = valorFrete; }
    public Integer getKmInicialVazio() { return kmInicialVazio; }
    public void setKmInicialVazio(Integer kmInicialVazio) { this.kmInicialVazio = kmInicialVazio; }
    public Integer getKmInicialCarregado() { return kmInicialCarregado; }
    public void setKmInicialCarregado(Integer kmInicialCarregado) { this.kmInicialCarregado = kmInicialCarregado; }
    public Integer getKmFinalCarregado() { return kmFinalCarregado; }
    public void setKmFinalCarregado(Integer kmFinalCarregado) { this.kmFinalCarregado = kmFinalCarregado; }
    public BigDecimal getConsumoMedioVazio() { return consumoMedioVazio; }
    public void setConsumoMedioVazio(BigDecimal consumoMedioVazio) { this.consumoMedioVazio = consumoMedioVazio; }
    public BigDecimal getConsumoMedioCarregado() { return consumoMedioCarregado; }
    public void setConsumoMedioCarregado(BigDecimal consumoMedioCarregado) { this.consumoMedioCarregado = consumoMedioCarregado; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public Instant getFinalizadaEm() { return finalizadaEm; }
    public void setFinalizadaEm(Instant finalizadaEm) { this.finalizadaEm = finalizadaEm; }
}
