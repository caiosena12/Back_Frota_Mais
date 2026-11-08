package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PedagioResponse(Long id, LocalDate data, BigDecimal valor) {

    public static PedagioResponse de(Despesa d) {
        return new PedagioResponse(d.getId(), d.getData(), d.getValor());
    }
}
