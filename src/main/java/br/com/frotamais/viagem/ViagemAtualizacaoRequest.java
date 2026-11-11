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
 * Substitui os dados do frete (campo ausente = valor apagado). O caminhão não
 * muda. dataFim e kmFinalCarregado só são aceitos em viagem já finalizada;
 * numa viagem em andamento eles vêm pela finalização.
 */
public record ViagemAtualizacaoRequest(
        @NotNull LocalDate dataInicio,
        @Size(max = 120) String origem,
        @Size(max = 120) String destino,
        @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorFrete,
        @PositiveOrZero @Max(9_999_999) Integer kmInicialVazio,
        @PositiveOrZero @Max(9_999_999) Integer kmInicialCarregado,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioVazio,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioCarregado,
        LocalDate dataFim,
        @PositiveOrZero @Max(9_999_999) Integer kmFinalCarregado,
        /** PUT do DONO substitui (ausente = sem motorista). O MOTORISTA não troca o responsável. */
        Long motoristaId) {
}
