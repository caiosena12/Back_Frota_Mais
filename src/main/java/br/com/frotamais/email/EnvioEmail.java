package br.com.frotamais.email;

/**
 * Envio de e-mail, desacoplado do provedor. Implementações:
 * SmtpEnvioEmail (SMTP configurado por variáveis de ambiente) e
 * EmailNaoConfigurado (sem SMTP: recusa, nunca finge que enviou).
 *
 * Deve lançar exceção se a mensagem não foi entregue ao servidor de e-mail,
 * para que a operação que dependia do envio seja desfeita.
 */
public interface EnvioEmail {

    void enviar(MensagemEmail mensagem);

    /** Há como enviar? Consultado antes de gravar, para recusar cedo quando não há SMTP. */
    default boolean disponivel() {
        return true;
    }
}
