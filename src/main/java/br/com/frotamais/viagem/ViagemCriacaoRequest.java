package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Abertura de um frete ("+ Nova Viagem"). Só os dados básicos são exigidos:
 * caminhão, data de início e uma quilometragem inicial. O resto pode vir
 * depois, pelo PUT, até a finalização.
 *
 * Não há empresaId nem valores calculados: a empresa vem do token e os
 * custos, o lucro e a margem são calculados pelo servidor.
 */
public record ViagemCriacaoRequest(
        @NotNull Long caminhaoId,
        /** Quem vai dirigir. MOTORISTA abrindo: ausente ou ele mesmo. DONO: um motorista vinculado, ele mesmo ou ausente. */
        Long motoristaId,
        @NotNull LocalDate dataInicio,
        @Size(max = 120) String origem,
        @Size(max = 120) String destino,
        @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorFrete,
        @PositiveOrZero @Max(9_999_999) Integer kmInicialVazio,
        @PositiveOrZero @Max(9_999_999) Integer kmInicialCarregado,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioVazio,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioCarregado) {
}
