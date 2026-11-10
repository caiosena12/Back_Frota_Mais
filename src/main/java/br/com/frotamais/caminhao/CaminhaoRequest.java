package br.com.frotamais.caminhao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e alteração (PUT = substituição integral) do caminhão.
 *
 * placa: normalizada pelo serviço (maiúsculas, sem espaço, hífen ou ponto),
 * para que "ABC-1234" e "abc 1234" sejam a mesma placa.
 * status: ATIVO ou INATIVO. No cadastro, ausente = ATIVO; na alteração é
 * obrigatório, para que esquecer o campo não reative um caminhão inativo.
 * EXCLUIDO só pelo DELETE (exclusão lógica).
 */
public record CaminhaoRequest(
        @NotBlank @Size(max = 12) String placa,
        @NotBlank @Size(max = 60) String marca,
        @NotBlank @Size(max = 80) String modelo,
        @NotNull @Min(1950) @Max(2100) Integer ano,
        @NotNull @Min(1) @Max(20) Integer eixos,
        StatusCaminhao status,
        @Positive Integer capacidadeTanqueLitros) {
}
