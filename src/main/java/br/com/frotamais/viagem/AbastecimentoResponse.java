package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AbastecimentoResponse(
        Long id,
        TipoCombustivel tipo,
        LocalDate data,
        LocalTime hora,
        BigDecimal litros,
        BigDecimal valorTotal) {

    public static AbastecimentoResponse de(Abastecimento a) {
        return new AbastecimentoResponse(a.getId(), a.getTipo(), a.getData(), a.getHora(), a.getLitros(), a.getValorTotal());
    }
}
