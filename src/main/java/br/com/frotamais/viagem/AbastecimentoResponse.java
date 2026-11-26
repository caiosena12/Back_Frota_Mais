package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * quilometragem: odômetro do caminhão no abastecimento; nula nos registros
 * antigos, em que não é conhecida.
 * hora: legado, só nos registros antigos que a informaram (não é mais recebida).
 */
public record AbastecimentoResponse(
        Long id,
        TipoCombustivel tipo,
        LocalDate data,
        Integer quilometragem,
        LocalTime hora,
        BigDecimal litros,
        BigDecimal valorTotal) {

    public static AbastecimentoResponse de(Abastecimento a) {
        return new AbastecimentoResponse(a.getId(), a.getTipo(), a.getData(), a.getQuilometragem(), a.getHora(),
                a.getLitros(), a.getValorTotal());
    }
}
