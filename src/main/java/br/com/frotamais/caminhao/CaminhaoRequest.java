package br.com.frotamais.caminhao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CaminhaoRequest(
        @NotBlank @Size(max = 8) String placa,
        @NotBlank @Size(max = 80) String modelo,
        @NotNull @Min(1950) @Max(2100) Integer ano,
        StatusCaminhao status,
        @Positive Integer capacidadeTanqueLitros) {
}
