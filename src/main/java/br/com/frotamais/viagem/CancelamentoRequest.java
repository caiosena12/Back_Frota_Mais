package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cancela uma viagem em andamento. O motivo é obrigatório, em texto livre.
 *
 * dataFim fecha o período da viagem (padrão: hoje), liberando o caminhão para
 * a próxima. Os demais campos são opcionais: registram até onde a operação
 * chegou, para o custo do que foi rodado poder ser calculado. Valor recebido
 * e despesas usam os campos e lançamentos normais da viagem.
 */
public record CancelamentoRequest(
        @NotBlank @Size(max = 1000) String motivo,
        LocalDate dataFim,
        @PositiveOrZero @Max(9_999_999) Integer kmFinalCarregado,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioVazio,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 2, fraction = 2) BigDecimal consumoMedioCarregado) {
}
