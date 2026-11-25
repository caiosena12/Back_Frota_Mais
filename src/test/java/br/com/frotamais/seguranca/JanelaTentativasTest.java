package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.frotamais.comum.TentativasExcedidasException;

/** Janela de tentativas, sem Spring, com relógio controlado. */
class JanelaTentativasTest {

    static final class Relogio extends Clock {
        private Instant agora = Instant.parse("2026-11-25T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final Relogio relogio = new Relogio();

    private JanelaTentativas janela(int limite, int maxChaves) {
        return new JanelaTentativas(limite, Duration.ofMinutes(5), maxChaves, relogio);
    }

    @Test
    void dentroDoLimitePassaEAcimaRecusaComOTempoRestante() {
        JanelaTentativas j = janela(3, 100);
        assertEquals(0, j.registrar("1.1.1.1"));
        assertEquals(0, j.registrar("1.1.1.1"));
        assertEquals(0, j.registrar("1.1.1.1"));
        assertEquals(300, j.registrar("1.1.1.1"));
        relogio.avancar(Duration.ofSeconds(100));
        assertEquals(200, j.registrar("1.1.1.1"));
        assertEquals(200, j.bloqueio("1.1.1.1"));
    }

    @Test
    void janelaVencidaLiberaSozinha() {
        JanelaTentativas j = janela(2, 100);
        j.registrar("a");
        j.registrar("a");
        assertTrue(j.registrar("a") > 0);
        relogio.avancar(Duration.ofMinutes(5).minusMillis(1));
        assertEquals(1, j.bloqueio("a"));
        relogio.avancar(Duration.ofMillis(1));
        assertEquals(0, j.bloqueio("a"));
        assertEquals(0, j.registrar("a"));
        assertEquals(0, j.registrar("a"));
        assertTrue(j.registrar("a") > 0);
    }

    @Test
    void chavesDiferentesContamSeparado() {
        JanelaTentativas j = janela(1, 100);
        assertEquals(0, j.registrar("10.0.0.1"));
        assertTrue(j.registrar("10.0.0.1") > 0);
        assertEquals(0, j.registrar("10.0.0.2"));
        assertEquals(0, j.registrar("motorista@exemplo.test"));
    }

    @Test
    void limparZeraAChave() {
        JanelaTentativas j = janela(1, 100);
        j.registrar("x");
        assertTrue(j.bloqueio("x") > 0);
        j.limpar("x");
        assertEquals(0, j.bloqueio("x"));
    }

    @Test
    void memoriaLimitadaDescartaAChaveMenosUsadaEMantemAAtacada() {
        JanelaTentativas j = janela(2, 1000);
        j.registrar("alvo");
        j.registrar("alvo");
        for (int i = 0; i < 50_000; i++) {
            j.registrar("ip-" + i);
            if (i % 500 == 0) {
                assertTrue(j.registrar("alvo") > 0, "a chave atacada continua bloqueada");
            }
        }
        assertTrue(j.chaves() <= 1000, () -> "chaves: " + j.chaves());
        assertTrue(j.bloqueio("alvo") > 0);
    }

    @Test
    void desligadoNaoContaNemBloqueia() {
        LimitesTentativas l = new LimitesTentativas(false, 100, 1, 5, 1, 15, 1, 60, 1, 15, relogio);
        for (int i = 0; i < 10; i++) {
            l.contarLoginPorIp("1.1.1.1");
            l.registrarFalhaLogin("a@exemplo.test");
            l.exigirContaLiberada("a@exemplo.test");
        }
    }

    @Test
    void ligadoLancaExcecaoComRetryAfter() {
        LimitesTentativas l = new LimitesTentativas(true, 100, 1, 5, 2, 15, 1, 60, 1, 15, relogio);
        l.contarLoginPorIp("1.1.1.1");
        TentativasExcedidasException e = assertThrows(TentativasExcedidasException.class,
                () -> l.contarLoginPorIp("1.1.1.1"));
        assertEquals(300, e.getSegundosParaLiberar());
        assertEquals(429, e.getStatus().value());

        l.registrarFalhaLogin("a@exemplo.test");
        l.exigirContaLiberada("a@exemplo.test");
        l.registrarFalhaLogin("a@exemplo.test");
        assertThrows(TentativasExcedidasException.class, () -> l.exigirContaLiberada("a@exemplo.test"));
        l.registrarSucessoLogin("a@exemplo.test");
        l.exigirContaLiberada("a@exemplo.test");
    }

    @Test
    void parametrosInvalidosNaoSobem() {
        assertThrows(IllegalArgumentException.class, () -> new JanelaTentativas(0, Duration.ofMinutes(1), 10, relogio));
        assertThrows(IllegalArgumentException.class, () -> new JanelaTentativas(1, Duration.ZERO, 10, relogio));
        assertThrows(IllegalArgumentException.class, () -> new JanelaTentativas(1, Duration.ofMinutes(1), 0, relogio));
    }
}
