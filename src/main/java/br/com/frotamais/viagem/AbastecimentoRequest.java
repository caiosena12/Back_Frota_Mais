package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import br.com.frotamais.comum.InteiroEstrito;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Abastecimento de diesel ou Arla (cadastro, correção e listas da abertura e
 * da viagem histórica).
 *
 * quilometragem: odômetro do caminhão no abastecimento, em km inteiros.
 * Obrigatória em todo abastecimento novo (RegrasLancamento.validarAbastecimento);
 * só a correção de um abastecimento antigo, gravado sem ela, pode mantê-la nula.
 * O horário deixou de ser recebido (contratos 1.2).
 */
public record AbastecimentoRequest(
        @NotNull TipoCombustivel tipo,
        @NotNull LocalDate data,
        @PositiveOrZero @Max(9_999_999) @JsonDeserialize(using = InteiroEstrito.class) Integer quilometragem,
        @NotNull @Positive @Digits(integer = 7, fraction = 3) BigDecimal litros,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorTotal) {
}
