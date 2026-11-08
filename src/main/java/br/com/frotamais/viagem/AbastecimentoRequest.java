package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AbastecimentoRequest(
        @NotNull TipoCombustivel tipo,
        @NotNull LocalDate data,
        @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal litros,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorTotal) {
}
