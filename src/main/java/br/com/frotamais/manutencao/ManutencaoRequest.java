package br.com.frotamais.manutencao;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Descrição livre do serviço; sem categorias nem palavras bloqueadas. */
public record ManutencaoRequest(
        @NotNull Long caminhaoId,
        @NotNull LocalDate data,
        @NotBlank @Size(max = 500) String descricao,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valor) {
}
