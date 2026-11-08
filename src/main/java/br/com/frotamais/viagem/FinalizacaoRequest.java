package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Encerra o frete na descarga. Os consumos médios vêm do computador de bordo;
 * se vierem nulos, valem os já gravados na viagem.
 */
public record FinalizacaoRequest(
        @NotNull LocalDate dataFim,
        @NotNull @PositiveOrZero @Max(9_999_999) Integer kmFinalCarregado,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioVazio,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioCarregado) {
}
