package br.com.frotamais.email;

import br.com.frotamais.comum.RegraNegocioException;

/** Usado quando não há SMTP configurado: a operação falha com 503 e é desfeita. */
class EmailNaoConfigurado implements EnvioEmail {

    @Override
    public boolean disponivel() {
        return false;
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        throw RegraNegocioException.indisponivel(
                "O envio de e-mail não está configurado no servidor. Nada foi enviado nem gravado.");
    }
}
