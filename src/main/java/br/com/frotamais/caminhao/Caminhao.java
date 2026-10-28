package br.com.frotamais.caminhao;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "caminhoes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "placa"}))
public class Caminhao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;

    @Column(nullable = false, length = 8)
    private String placa;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(nullable = false)
    private Integer ano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusCaminhao status = StatusCaminhao.ATIVO;

    @Column(name = "capacidade_tanque_litros")
    private Integer capacidadeTanqueLitros;

    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
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
