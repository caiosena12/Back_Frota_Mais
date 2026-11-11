package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cadastro retroativo de um frete esquecido, já finalizado. Exige os mesmos
 * dados de uma finalização e traz os lançamentos do frete, gravados junto,
 * na mesma transação. As datas são as do percurso real.
 *
 * kmInicialVazio e consumoMedioVazio são opcionais (frete sem deslocamento
 * vazio); se houver vazio, o consumo vazio é exigido. Listas ausentes valem
 * como vazias.
 */
public record ViagemHistoricaRequest(
        @NotNull Long caminhaoId,
        /** Quem dirigiu, se conhecido (motorista ou dono da empresa). */
        Long motoristaId,
        @NotNull LocalDate dataInicio,
        @NotNull LocalDate dataFim,
        @NotBlank @Size(max = 120) String origem,
        @NotBlank @Size(max = 120) String destino,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal valorFrete,
        @PositiveOrZero @Max(9_999_999) Integer kmInicialVazio,
        @NotNull @PositiveOrZero @Max(9_999_999) Integer kmInicialCarregado,
        @NotNull @PositiveOrZero @Max(9_999_999) Integer kmFinalCarregado,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioVazio,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioCarregado,
        @Size(max = 200) List<@NotNull @Valid AbastecimentoRequest> abastecimentos,
        @Size(max = 200) List<@NotNull @Valid PedagioRequest> pedagios,
        @Size(max = 200) List<@NotNull @Valid DespesaExtraRequest> despesasExtras) {

    List<AbastecimentoRequest> abastecimentosOuVazio() {
        return abastecimentos == null ? List.of() : abastecimentos;
    }

    List<PedagioRequest> pedagiosOuVazio() {
        return pedagios == null ? List.of() : pedagios;
    }

    List<DespesaExtraRequest> despesasExtrasOuVazio() {
        return despesasExtras == null ? List.of() : despesasExtras;
    }
}
