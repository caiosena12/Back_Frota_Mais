package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaExtraResponse(Long id, String descricao, LocalDate data, BigDecimal valor) {

    public static DespesaExtraResponse de(Despesa d) {
        return new DespesaExtraResponse(d.getId(), d.getDescricao(), d.getData(), d.getValor());
    }
}
