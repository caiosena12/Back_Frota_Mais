package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DespesaExtraRequest(
        @NotBlank @Size(max = 200) String descricao,
        @NotNull LocalDate data,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor) {
}
