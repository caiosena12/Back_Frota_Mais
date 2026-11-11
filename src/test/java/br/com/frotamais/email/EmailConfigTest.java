package br.com.frotamais.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import br.com.frotamais.comum.RegraNegocioException;

/** Sem SMTP ou sem remetente, o sistema recusa o envio (503); nunca finge que enviou. */
class EmailConfigTest {

    private static final MensagemEmail MENSAGEM = new MensagemEmail("a@b.com", "Assunto", "Texto");

    @Test
    void semJavaMailSenderUsaImplementacaoQueRecusa() {
        EnvioEmail envio = EmailConfig.escolher(null, "frota@empresa.com");
        assertInstanceOf(EmailNaoConfigurado.class, envio);
        assertFalse(envio.disponivel());
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> envio.enviar(MENSAGEM));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    @Test
    void semRemetenteTambemRecusa() {
        assertInstanceOf(EmailNaoConfigurado.class, EmailConfig.escolher(new JavaMailSenderImpl(), "  "));
    }

    @Test
    void comSmtpERemetenteUsaSmtp() {
        EnvioEmail envio = EmailConfig.escolher(new JavaMailSenderImpl(), "frota@empresa.com");
        assertInstanceOf(SmtpEnvioEmail.class, envio);
        assertTrue(envio.disponivel());
    }

    @Test
    void falhaDoServidorSmtpViraErro503() {
        JavaMailSenderImpl semServidor = new JavaMailSenderImpl();
        semServidor.setHost("localhost");
        semServidor.setPort(1); // porta sem servidor: conexão recusada
        EnvioEmail envio = EmailConfig.escolher(semServidor, "frota@empresa.com");
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> envio.enviar(MENSAGEM));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }
}
