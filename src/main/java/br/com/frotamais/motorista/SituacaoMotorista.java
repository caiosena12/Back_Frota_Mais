package br.com.frotamais.motorista;

import br.com.frotamais.usuario.Usuario;

public enum SituacaoMotorista {
    /** Convidado que ainda não criou a senha. */
    CONVITE_PENDENTE,
    ATIVO,
    /** Desativado pelo dono: não faz login, tokens emitidos param de valer. */
    INATIVO;

    static SituacaoMotorista de(Usuario u) {
        if (u.isAtivacaoPendente()) {
            return CONVITE_PENDENTE;
        }
        return u.isAtivo() ? ATIVO : INATIVO;
    }
}
