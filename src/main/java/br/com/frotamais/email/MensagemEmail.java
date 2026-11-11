package br.com.frotamais.email;

/** E-mail em texto simples. */
public record MensagemEmail(String para, String assunto, String texto) {
}
