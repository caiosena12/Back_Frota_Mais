package br.com.frotamais.viagem;

import java.math.BigDecimal;

/**
 * Política usada num cálculo: versão das fórmulas e limites de plausibilidade.
 * Num frete finalizado, é a política gravada na fotografia financeira.
 */
public record PoliticaCalculoAplicada(
        String versao,
        BigDecimal precoDieselMinimo,
        BigDecimal precoDieselMaximo,
        BigDecimal consumoMinimo,
        BigDecimal consumoMaximo) {

    public static PoliticaCalculoAplicada de(LimitesPlausibilidade limites) {
        return new PoliticaCalculoAplicada(CalculadoraRentabilidade.VERSAO_POLITICA,
                limites.precoDieselMinimo(), limites.precoDieselMaximo(),
                limites.consumoMinimo(), limites.consumoMaximo());
    }
}
