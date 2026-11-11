package br.com.frotamais.manutencao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.comum.Datas;
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
 * Manutenção de um caminhão: data, descrição livre e valor gasto. Faz parte
 * do histórico do caminhão e do resultado mensal, nunca de um frete.
 * Exclusão lógica: excluidaEm preenchido tira a manutenção das listas e dos
 * totais, mas o registro continua no banco.
 */
@Entity
@Table(name = "manutencoes")
public class Manutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caminhao_id", nullable = false)
    private Caminhao caminhao;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Datas.agora();

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm = Datas.agora();

    @Column(name = "excluida_em")
    private Instant excluidaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "excluida_por_id")
    private Usuario excluidaPor;

    public boolean isExcluida() {
        return excluidaEm != null;
    }

    public void excluir(Usuario por) {
        this.excluidaEm = Datas.agora();
        this.excluidaPor = por;
    }

    public void marcarAlteracao() {
        this.atualizadaEm = Datas.agora();
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public Caminhao getCaminhao() { return caminhao; }
    public void setCaminhao(Caminhao caminhao) { this.caminhao = caminhao; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public Instant getExcluidaEm() { return excluidaEm; }
}
