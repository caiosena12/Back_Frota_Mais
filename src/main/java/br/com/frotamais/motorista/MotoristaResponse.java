package br.com.frotamais.motorista;

import java.time.Instant;
import java.util.List;

import br.com.frotamais.usuario.Usuario;

/** conviteExpiraEm: validade do convite pendente mais recente, ou null. */
public record MotoristaResponse(
        Long id,
        String nome,
        String email,
        SituacaoMotorista situacao,
        List<Long> caminhaoIds,
        Instant conviteExpiraEm) {

    static MotoristaResponse de(Usuario u, List<Long> caminhaoIds, Instant conviteExpiraEm) {
        return new MotoristaResponse(u.getId(), u.getNome(), u.getEmail(), SituacaoMotorista.de(u),
                caminhaoIds, conviteExpiraEm);
    }
}
