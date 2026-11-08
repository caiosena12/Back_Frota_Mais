package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Barreiras contra erros grosseiros de digitação (não são referências de
 * mercado). Configuradas em app.viagens.limites.* e validadas na subida: a
 * aplicação não inicia com valor ausente, não positivo ou mínimo ≥ máximo.
 *
 * Valores fora dos limites são recusados na entrada (400). Se um dado já
 * gravado ficar fora deles (por exemplo, após mudar a configuração), o frete
 * passa a ter cálculo incompleto, com o motivo nas pendências; nada vira zero.
 */
@Validated
@ConfigurationProperties(prefix = "app.viagens.limites")
public record LimitesPlausibilidade(
        @NotNull @Positive BigDecimal precoDieselMinimo,
        @NotNull @Positive BigDecimal precoDieselMaximo,
        @NotNull @Positive BigDecimal consumoMinimo,
        @NotNull @Positive BigDecimal consumoMaximo) {

    public LimitesPlausibilidade {
        exigirOrdem(precoDieselMinimo, precoDieselMaximo, "preco-diesel");
        exigirOrdem(consumoMinimo, consumoMaximo, "consumo");
    }

    private static void exigirOrdem(BigDecimal minimo, BigDecimal maximo, String nome) {
        if (minimo != null && maximo != null && minimo.compareTo(maximo) >= 0) {
            throw new IllegalArgumentException("app.viagens.limites." + nome
                    + "-minimo deve ser menor que app.viagens.limites." + nome + "-maximo.");
        }
    }

    public boolean precoValido(BigDecimal preco) {
        return dentro(preco, precoDieselMinimo, precoDieselMaximo);
    }

    public boolean consumoValido(BigDecimal consumo) {
        return dentro(consumo, consumoMinimo, consumoMaximo);
    }

    public String faixaPreco() {
        return "R$ " + br(precoDieselMinimo) + " a R$ " + br(precoDieselMaximo) + " por litro";
    }

    public String faixaConsumo() {
        return br(consumoMinimo) + " a " + br(consumoMaximo) + " km/L";
    }

    /** Número com 2 casas e vírgula decimal, para mensagens. */
    public static String br(BigDecimal valor) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        return f.format(valor);
    }

    private static boolean dentro(BigDecimal valor, BigDecimal minimo, BigDecimal maximo) {
        return valor != null && valor.compareTo(minimo) >= 0 && valor.compareTo(maximo) <= 0;
    }
}
