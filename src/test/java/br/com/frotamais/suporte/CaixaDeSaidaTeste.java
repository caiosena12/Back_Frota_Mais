package br.com.frotamais.suporte;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.email.EnvioEmail;
import br.com.frotamais.email.MensagemEmail;

/**
 * Substitui o envio real de e-mail em toda a suíte (só existe no classpath de
 * teste): guarda as mensagens para os testes lerem o link do convite. Nenhum
 * e-mail sai da máquina.
 */
@Component
@Primary
public class CaixaDeSaidaTeste implements EnvioEmail {

    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private final List<MensagemEmail> enviadas = new ArrayList<>();
    private boolean indisponivel;

    @Override
    public synchronized boolean disponivel() {
        return !indisponivel;
    }

    @Override
    public synchronized void enviar(MensagemEmail mensagem) {
        if (indisponivel) {
            throw RegraNegocioException.indisponivel("E-mail indisponível (simulado).");
        }
        enviadas.add(mensagem);
    }

    /** Simula servidor sem SMTP configurado. */
    public synchronized void simularIndisponivel(boolean valor) {
        this.indisponivel = valor;
    }

    public synchronized List<MensagemEmail> paraEndereco(String email) {
        return enviadas.stream().filter(m -> m.para().equalsIgnoreCase(email)).toList();
    }

    /** Token do link do último e-mail enviado ao endereço. */
    public synchronized String ultimoToken(String email) {
        List<MensagemEmail> lista = paraEndereco(email);
        if (lista.isEmpty()) {
            throw new AssertionError("Nenhum e-mail enviado para " + email);
        }
        Matcher m = TOKEN.matcher(lista.get(lista.size() - 1).texto());
        if (!m.find()) {
            throw new AssertionError("E-mail sem link de convite.");
        }
        return m.group(1);
    }
}
