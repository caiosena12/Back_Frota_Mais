package br.com.frotamais.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contador de tentativas por chave (IP ou e-mail) em janela fixa: até
 * {@code limite} tentativas por {@code janela}; a seguinte é recusada até a
 * janela terminar. Nada é permanente: a chave volta a valer sozinha.
 *
 * Memória limitada: no máximo {@code maxChaves} chaves, descartando a menos
 * usada recentemente (LRU) quando o limite é atingido. Uma chave atacada
 * continuamente fica "quente" e não é descartada. Janelas vencidas são
 * reiniciadas quando a chave volta a aparecer.
 *
 * Só existe na memória desta instância: um reinício zera os contadores, e
 * várias instâncias contam separadamente (ver docs/DEPLOYMENT_PRODUCTION.md).
 */
final class JanelaTentativas {

    private static final class Contador {
        private Instant inicio;
        private int tentativas;

        private Contador(Instant inicio) {
            this.inicio = inicio;
        }
    }

    private final int limite;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Contador> contadores;

    JanelaTentativas(int limite, Duration janela, int maxChaves, Clock relogio) {
        if (limite < 1 || janela.isNegative() || janela.isZero() || maxChaves < 1) {
            throw new IllegalArgumentException("Limite, janela e máximo de chaves devem ser positivos.");
        }
        this.limite = limite;
        this.janela = janela;
        this.relogio = relogio;
        this.contadores = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Contador> mais) {
                return size() > maxChaves;
            }
        };
    }

    /**
     * Conta uma tentativa. Devolve 0 se ela está dentro do limite, ou os
     * segundos até a janela liberar (a tentativa recusada não é contada).
     */
    synchronized long registrar(String chave) {
        Instant agora = relogio.instant();
        Contador c = atual(chave, agora);
        if (c.tentativas >= limite) {
            return segundosRestantes(c, agora);
        }
        c.tentativas++;
        return 0;
    }

    /** Segundos até liberar, ou 0 se a chave ainda aceita tentativas. Não conta. */
    synchronized long bloqueio(String chave) {
        Instant agora = relogio.instant();
        Contador c = contadores.get(chave);
        if (c == null || vencido(c, agora) || c.tentativas < limite) {
            return 0;
        }
        return segundosRestantes(c, agora);
    }

    synchronized void limpar(String chave) {
        contadores.remove(chave);
    }

    synchronized int chaves() {
        return contadores.size();
    }

    private Contador atual(String chave, Instant agora) {
        Contador c = contadores.get(chave);
        if (c == null) {
            c = new Contador(agora);
            contadores.put(chave, c);
        } else if (vencido(c, agora)) {
            c.inicio = agora;
            c.tentativas = 0;
        }
        return c;
    }

    private boolean vencido(Contador c, Instant agora) {
        return !agora.isBefore(c.inicio.plus(janela));
    }

    private long segundosRestantes(Contador c, Instant agora) {
        long ms = Duration.between(agora, c.inicio.plus(janela)).toMillis();
        return Math.max(1, (ms + 999) / 1000);
    }
}
