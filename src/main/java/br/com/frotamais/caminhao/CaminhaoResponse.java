package br.com.frotamais.caminhao;

import java.time.Instant;

public record CaminhaoResponse(
        Long id,
        String placa,
        String marca,
        String modelo,
        Integer ano,
        Integer eixos,
        StatusCaminhao status,
        Integer capacidadeTanqueLitros,
        Instant inativadoEm,
        Instant excluidoEm) {

    public static CaminhaoResponse de(Caminhao c) {
        return new CaminhaoResponse(
                c.getId(),
                c.getPlaca(),
                c.getMarca(),
                c.getModelo(),
                c.getAno(),
                c.getEixos(),
                c.getStatus(),
                c.getCapacidadeTanqueLitros(),
                c.getInativadoEm(),
                c.getExcluidoEm());
    }
}
