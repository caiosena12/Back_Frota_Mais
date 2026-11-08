package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Referência de preço do diesel de uma viagem: os totais (litros e valor pago)
 * de onde sai a média ponderada, e a origem desses totais.
 *
 * Guardar os totais, e não só a média, permite calcular o custo com uma única
 * divisão (km × total pago ÷ (consumo × litros)), sem arredondar a média antes.
 */
public record PrecoDiesel(
        BigDecimal totalPago,
        BigDecimal totalLitros,
        OrigemPrecoDiesel origem,
        Long viagemReferenciaId,
        Instant consolidadaEm) {

    /** Referência calculada na hora (ainda não consolidada). */
    public PrecoDiesel(BigDecimal totalPago, BigDecimal totalLitros, OrigemPrecoDiesel origem, Long viagemReferenciaId) {
        this(totalPago, totalLitros, origem, viagemReferenciaId, null);
    }

    public static PrecoDiesel semReferencia() {
        return new PrecoDiesel(null, null, OrigemPrecoDiesel.SEM_REFERENCIA, null, null);
    }

    public boolean disponivel() {
        return origem != OrigemPrecoDiesel.SEM_REFERENCIA;
    }

    /** Preço médio ponderado em R$/L, arredondado a 4 casas para exibição. */
    public BigDecimal precoMedio() {
        if (!disponivel()) {
            return null;
        }
        return totalPago.divide(totalLitros, MathContext.DECIMAL128)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
