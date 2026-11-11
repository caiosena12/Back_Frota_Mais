package br.com.frotamais.viagem;

import java.time.Instant;

import br.com.frotamais.usuario.Perfil;

/**
 * Um campo alterado numa viagem encerrada. registroId: id da viagem ou do
 * lançamento. perfil: o de quem alterou, no momento da alteração. Inclusão de
 * lançamento (viagem cancelada) tem valorAnterior nulo.
 */
public record AlteracaoViagemResponse(
        Long id,
        Instant alteradaEm,
        Long usuarioId,
        String usuarioNome,
        Perfil perfil,
        AlteracaoViagem.Entidade entidade,
        Long registroId,
        String campo,
        String valorAnterior,
        String valorNovo) {

    static AlteracaoViagemResponse de(AlteracaoViagem a) {
        return new AlteracaoViagemResponse(a.getId(), a.getAlteradaEm(), a.getUsuario().getId(),
                a.getUsuario().getNome(), a.getPerfil(), a.getEntidade(), a.getRegistroId(), a.getCampo(),
                a.getValorAnterior(), a.getValorNovo());
    }
}
