package br.com.frotamais.caminhao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "caminhoes")
public class Caminhao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String placa;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Column(length = 40)
    private String apelido;

    @Column(name = "capacidade_tanque_litros")
    private Integer capacidadeTanqueLitros;

    public Long getId() {
        return id;
    }

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public Integer getCapacidadeTanqueLitros() {
        return capacidadeTanqueLitros;
    }

    public void setCapacidadeTanqueLitros(Integer capacidadeTanqueLitros) {
        this.capacidadeTanqueLitros = capacidadeTanqueLitros;
    }
}
