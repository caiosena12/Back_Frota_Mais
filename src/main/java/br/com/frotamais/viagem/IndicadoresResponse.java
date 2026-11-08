package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Totais dos fretes FINALIZADOS cuja dataFim está no período.
 *
 * Os totais (financeiros e de km) consideram só os fretes com cálculo
 * completo; os demais são listados em viagensComCalculoIncompleto, para que um
 * custo desconhecido nunca apareça como zero. Manutenção não entra.
 */
public record IndicadoresResponse(
        LocalDate de,
        LocalDate ate,
        Long caminhaoId,
        String criterioPeriodo,
        int viagensFinalizadas,
        int viagensConsideradas,
        List<Long> viagensComCalculoIncompleto,
        BigDecimal receitaTotal,
        BigDecimal custoDieselVazio,
        BigDecimal custoDieselCarregado,
        BigDecimal custoArla,
        BigDecimal pedagios,
        BigDecimal despesasExtras,
        BigDecimal custoOperacionalTotal,
        BigDecimal lucroTotal,
        BigDecimal margemPercentual,
        long kmVazio,
        long kmCarregado) {
}
