package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Rentabilidade de um frete, sempre calculada pelo servidor.
 *
 * resultadoConsolidadoEm preenchido: é a fotografia financeira do frete
 * finalizado, gravada na finalização (ou na última correção explícita deste
 * frete) com a política de cálculo daquele momento (politicaCalculo). Mudanças
 * nos limites globais ou em outros fretes não a alteram. Nulo: viagem em
 * andamento, calculada a cada consulta com a política atual.
 *
 * referenciaPrecoConsolidadaEm: quando a referência de preço do diesel foi
 * congelada. Só muda numa correção da viagem ou do diesel dela; corrigir um
 * pedágio ou uma despesa recalcula o resultado com o mesmo preço.
 *
 * Campos nulos significam "não calculável com os dados atuais"; o motivo está
 * em pendencias. Manutenção não faz parte deste cálculo: é despesa do caminhão.
 */
public record RentabilidadeResponse(
        BigDecimal receita,
        Integer kmVazio,
        Integer kmCarregado,
        BigDecimal litrosEstimadosVazio,
        BigDecimal litrosEstimadosCarregado,
        BigDecimal precoMedioDiesel,
        OrigemPrecoDiesel origemPrecoDiesel,
        Long viagemReferenciaPrecoDieselId,
        Instant referenciaPrecoConsolidadaEm,
        BigDecimal custoDieselVazio,
        BigDecimal custoDieselCarregado,
        BigDecimal custoArla,
        BigDecimal pedagios,
        BigDecimal despesasExtras,
        BigDecimal custoOperacionalTotal,
        BigDecimal lucro,
        BigDecimal margemPercentual,
        boolean calculoCompleto,
        List<String> pendencias,
        PoliticaCalculoAplicada politicaCalculo,
        Instant resultadoConsolidadoEm) {
}
