package br.com.frotamais.caminhao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import br.com.frotamais.empresa.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
/**
 * Placa única por empresa entre os caminhões não excluídos: índice único
 * parcial uk_caminhoes_empresa_placa_operacional (migration V3).
 */
@Table(name = "caminhoes")
public class Caminhao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(nullable = false, length = 8)
    private String placa;

    @Column(length = 60)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(nullable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusCaminhao status = StatusCaminhao.ATIVO;

    @Column(name = "capacidade_tanque_litros")
    private Integer capacidadeTanqueLitros;

    private Integer eixos;

    @Column(name = "inativado_em")
    private Instant inativadoEm;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    /** Data (no fuso do produto) em que o caminhão foi inativado, ou null. */
    public LocalDate dataInativacao(ZoneId fuso) {
        return inativadoEm == null ? null : LocalDate.ofInstant(inativadoEm, fuso);
    }

    public boolean isExcluido() {
        return status == StatusCaminhao.EXCLUIDO;
    }

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public Integer getEixos() { return eixos; }
    public void setEixos(Integer eixos) { this.eixos = eixos; }
    public Instant getInativadoEm() { return inativadoEm; }
    public void setInativadoEm(Instant inativadoEm) { this.inativadoEm = inativadoEm; }
    public Instant getExcluidoEm() { return excluidoEm; }
    public void setExcluidoEm(Instant excluidoEm) { this.excluidoEm = excluidoEm; }
    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }
    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }
    public StatusCaminhao getStatus() { return status; }
    public void setStatus(StatusCaminhao status) { this.status = status; }
    public Integer getCapacidadeTanqueLitros() { return capacidadeTanqueLitros; }
    public void setCapacidadeTanqueLitros(Integer capacidadeTanqueLitros) {
        this.capacidadeTanqueLitros = capacidadeTanqueLitros;
    }
}
