package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Só o valor efetivamente pago; sem desconto de tag, eixos ou horário. */
public record PedagioRequest(
        @NotNull LocalDate data,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor) {
}
