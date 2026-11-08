package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Totais dos fretes FINALIZADOS cuja dataFim está no período.
 *
 * Totais oficiais (financeiros e de km): só fretes com cálculo completo,
 * lidos da fotografia financeira de cada frete. Os incompletos ficam fora
 * deles, para que um custo desconhecido nunca apareça como zero: são contados
 * em viagensIncompletas, listados em viagensComCalculoIncompleto, e
 * valorFreteIncompletasNaoIncluido é só informativo (NÃO entra na receita
 * total nem em nenhum outro indicador).
 *
 * margemPercentual é ponderada: lucroTotal ÷ receitaTotal × 100.
 * Manutenção não entra.
 */
public record IndicadoresResponse(
        LocalDate de,
        LocalDate ate,
        Long caminhaoId,
        String criterioPeriodo,
        int viagensFinalizadas,
        int viagensConsideradas,
        int viagensIncompletas,
        List<Long> viagensComCalculoIncompleto,
        BigDecimal valorFreteIncompletasNaoIncluido,
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
