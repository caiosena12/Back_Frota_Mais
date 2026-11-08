package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Rentabilidade de um frete, sempre calculada pelo servidor.
 *
 * referenciaPrecoConsolidadaEm preenchido: o preço do diesel foi congelado na
 * finalização (ou na última correção explícita deste frete). Nulo: viagem em
 * andamento, com preço calculado a cada consulta.
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
        List<String> pendencias) {
}
