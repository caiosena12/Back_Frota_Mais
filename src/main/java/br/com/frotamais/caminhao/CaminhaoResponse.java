package br.com.frotamais.caminhao;

public record CaminhaoResponse(
        Long id,
        String placa,
        String modelo,
        Integer ano,
        StatusCaminhao status,
        Integer capacidadeTanqueLitros) {

    public static CaminhaoResponse de(Caminhao c) {
        return new CaminhaoResponse(
                c.getId(),
                c.getPlaca(),
                c.getModelo(),
                c.getAno(),
                c.getStatus(),
                c.getCapacidadeTanqueLitros());
    }
}
