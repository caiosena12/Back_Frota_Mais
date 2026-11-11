package br.com.frotamais.viagem;

import java.time.Instant;

/** Um campo corrigido numa viagem encerrada. registroId: id da viagem ou do lançamento. */
public record AlteracaoViagemResponse(
        Long id,
        Instant alteradaEm,
        Long usuarioId,
        String usuarioNome,
        AlteracaoViagem.Entidade entidade,
        Long registroId,
        String campo,
        String valorAnterior,
        String valorNovo) {

    static AlteracaoViagemResponse de(AlteracaoViagem a) {
        return new AlteracaoViagemResponse(a.getId(), a.getAlteradaEm(), a.getUsuario().getId(),
                a.getUsuario().getNome(), a.getEntidade(), a.getRegistroId(), a.getCampo(),
                a.getValorAnterior(), a.getValorNovo());
    }
}
