package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AbastecimentoResponse(
        Long id,
        TipoCombustivel tipo,
        LocalDate data,
        BigDecimal litros,
        BigDecimal valorTotal) {

    public static AbastecimentoResponse de(Abastecimento a) {
        return new AbastecimentoResponse(a.getId(), a.getTipo(), a.getData(), a.getLitros(), a.getValorTotal());
    }
}
