package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Soma dos abastecimentos de diesel de uma viagem, usada para o preço médio. */
public record TotaisDiesel(
        Long viagemId,
        Long caminhaoId,
        LocalDate dataInicio,
        BigDecimal litros,
        BigDecimal valorPago) {
}
