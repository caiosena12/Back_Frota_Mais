package br.com.frotamais.caminhao;

public record CaminhaoResponse(
        Long id,
        String placa,
        String modelo,
        String apelido,
        Integer capacidadeTanqueLitros) {

    public static CaminhaoResponse de(Caminhao c) {
        return new CaminhaoResponse(
                c.getId(),
                c.getPlaca(),
                c.getModelo(),
                c.getApelido(),
                c.getCapacidadeTanqueLitros());
    }
}
