package br.com.frotamais.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import br.com.frotamais.comum.RegraNegocioException;

/** Envio pelo JavaMailSender do Spring (spring.mail.*). Nunca registra o conteúdo da mensagem. */
class SmtpEnvioEmail implements EnvioEmail {

    private static final Logger log = LoggerFactory.getLogger(SmtpEnvioEmail.class);

    private final JavaMailSender mailSender;
    private final String remetente;

    SmtpEnvioEmail(JavaMailSender mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(MensagemEmail mensagem) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(remetente);
        m.setTo(mensagem.para());
        m.setSubject(mensagem.assunto());
        m.setText(mensagem.texto());
        try {
            mailSender.send(m);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail pelo SMTP: {}", e.getClass().getSimpleName());
            throw RegraNegocioException.indisponivel(
                    "Não foi possível enviar o e-mail agora. Nada foi gravado; tente novamente.");
        }
    }
}
