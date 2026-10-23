package br.com.frotamais.caminhao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CaminhaoRequest(
        @NotBlank @Size(max = 8) String placa,
        @NotBlank @Size(max = 80) String modelo,
        @Size(max = 40) String apelido,
        @Positive Integer capacidadeTanqueLitros) {
}
