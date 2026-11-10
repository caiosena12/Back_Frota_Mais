package br.com.frotamais.convite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Token do convite e configuração, sem banco. */
class ConviteTokenTest {

    @Test
    void tokenTem256BitsEmBase64UrlENaoSeRepete() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String t = ConviteService.gerarToken();
            assertEquals(43, t.length());
            assertTrue(t.matches("[A-Za-z0-9_-]+"), t);
            assertTrue(vistos.add(t));
        }
    }

    @Test
    void hashESha256HexDeterministico() {
        String t = ConviteService.gerarToken();
        assertEquals(ConviteService.hash(t), ConviteService.hash(t));
        assertEquals(64, ConviteService.hash(t).length());
        assertNotEquals(t, ConviteService.hash(t));
    }

    @Test
    void validadeConfiguravelComLimites() {
        assertEquals(Duration.ofHours(24), new ConfigConvites(24, "https://x").validade());
        assertEquals(Duration.ofHours(72), new ConfigConvites(72, "https://x").validade());
        assertThrows(IllegalArgumentException.class, () -> new ConfigConvites(0, "https://x"));
        assertThrows(IllegalArgumentException.class, () -> new ConfigConvites(721, "https://x"));
        assertTrue(!new ConfigConvites(24, " ").urlConfigurada());
    }
}
