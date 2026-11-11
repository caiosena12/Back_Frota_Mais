package br.com.frotamais.manutencao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** excluidaEm preenchido: manutenção excluída (só aparece quando pedida). */
public record ManutencaoResponse(
        Long id,
        Long caminhaoId,
        String placa,
        LocalDate data,
        String descricao,
        BigDecimal valor,
        Instant criadaEm,
        Instant atualizadaEm,
        Instant excluidaEm) {

    static ManutencaoResponse de(Manutencao m) {
        return new ManutencaoResponse(m.getId(), m.getCaminhao().getId(), m.getCaminhao().getPlaca(),
                m.getData(), m.getDescricao(), m.getValor(), m.getCriadaEm(), m.getAtualizadaEm(),
                m.getExcluidaEm());
    }
}
