package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Totais dos fretes FINALIZADOS cuja dataFim está no período, e das
 * manutenções cuja data está no período.
 *
 * Totais oficiais (financeiros e de km): só fretes com cálculo completo,
 * lidos da fotografia financeira de cada frete. Os incompletos ficam fora
 * deles, para que um custo desconhecido nunca apareça como zero: são contados
 * em viagensIncompletas, listados em viagensComCalculoIncompleto, e
 * valorFreteIncompletasNaoIncluido é só informativo (NÃO entra na receita
 * total nem em nenhum outro indicador).
 *
 * margemPercentual é ponderada: lucroTotal ÷ receitaTotal × 100.
 *
 * Indicadores por km, consumo e diesel (mesmos fretes completos; nulos quando
 * não há base para a divisão):
 * - custoPorKm e lucroPorKm: custoOperacionalTotal e lucroTotal ÷ km totais
 *   (vazio + carregado);
 * - percentualKmVazio: kmVazio ÷ km totais × 100;
 * - consumoMedioKmPorLitro: km totais ÷ litros estimados (média ponderada
 *   dos consumos informados, não de um consumo cadastrado);
 * - dieselPago: soma dos abastecimentos de diesel desses fretes;
 *   dieselEstimado: custoDieselVazio + custoDieselCarregado (o que entra no
 *   custo); diferencaDieselPagoEstimado = pago − estimado. Informativos: o
 *   valor pago nunca é somado ao custo (sem dupla contagem).
 *
 * Manutenções (não pertencem a nenhum frete): quantidadeManutencoes e
 * custoManutencoes no período, e resultadoAposManutencoes = lucroTotal −
 * custoManutencoes, o resultado do período do caminhão ou da empresa.
 *
 * Viagens canceladas (bloco separado; nunca contam como fretes concluídos
 * nem entram nos indicadores acima): entram pelo mês da data do
 * cancelamento (data final da viagem cancelada). receitaCanceladas soma os
 * valores recebidos informados no campo do frete; custoCanceladas soma os
 * custos conhecidos (Arla, pedágios, despesas e o diesel quando calculável);
 * as canceladas cujo diesel não pôde ser calculado ficam listadas em
 * canceladasComCustoIncompleto (o diesel delas não entra como zero: fica de
 * fora da soma). resultadoCanceladas = receitaCanceladas − custoCanceladas.
 *
 * resultadoFinanceiroPeriodo = lucroTotal + resultadoCanceladas −
 * custoManutencoes: o financeiro geral do período, sem dupla contagem (cada
 * viagem está em um único bloco).
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
        long kmCarregado,
        BigDecimal custoPorKm,
        BigDecimal lucroPorKm,
        BigDecimal percentualKmVazio,
        BigDecimal consumoMedioKmPorLitro,
        BigDecimal dieselPago,
        BigDecimal dieselEstimado,
        BigDecimal diferencaDieselPagoEstimado,
        long quantidadeManutencoes,
        BigDecimal custoManutencoes,
        BigDecimal resultadoAposManutencoes,
        int viagensCanceladas,
        List<Long> canceladasComCustoIncompleto,
        BigDecimal receitaCanceladas,
        BigDecimal custoCanceladas,
        BigDecimal resultadoCanceladas,
        BigDecimal resultadoFinanceiroPeriodo) {
}
