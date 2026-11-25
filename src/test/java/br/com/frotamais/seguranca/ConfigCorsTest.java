package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/** Validação das origens de CORS, sem subir o contexto. */
class ConfigCorsTest {

    @Test
    void vazioSemProducaoNaoLiberaNenhumaOrigem() {
        assertEquals(List.of(), ConfigCors.origens("", false));
        assertEquals(List.of(), ConfigCors.origens(null, false));
        assertEquals(List.of(), ConfigCors.origens(" , ", false));
        assertNull(ConfigCors.fonte(List.of()).getCorsConfiguration(new MockHttpServletRequest("GET", "/api/caminhoes")));
    }

    @Test
    void vazioEmProducaoImpedeASubida() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ConfigCors.origens("  ", true));
        assertTrue(e.getMessage().contains("CORS_ORIGENS_PERMITIDAS"));
    }

    @Test
    void normalizaEDeduplica() {
        assertEquals(List.of("https://app.exemplo.test", "https://outro.exemplo.test:8443"),
                ConfigCors.origens(" https://APP.exemplo.test/ ,https://app.exemplo.test,https://outro.exemplo.test:8443", true));
    }

    @Test
    void recusaCuringaCaminhoEEsquemaInvalido() {
        for (String ruim : List.of("*", "https://*.exemplo.test", "https://app.exemplo.test/api",
                "https://app.exemplo.test?x=1", "ftp://app.exemplo.test", "app.exemplo.test",
                "https://usuario@app.exemplo.test", "https://app.exemplo.test#a")) {
            assertThrows(IllegalStateException.class, () -> ConfigCors.origens(ruim, false), ruim);
        }
    }

    @Test
    void httpSoForaDaProducao() {
        assertEquals(List.of("http://localhost:4200"), ConfigCors.origens("http://localhost:4200", false));
        assertThrows(IllegalStateException.class, () -> ConfigCors.origens("http://app.exemplo.test", true));
    }

    @Test
    void configuracaoRestrita() {
        CorsConfiguration cfg = ConfigCors.fonte(List.of("https://app.exemplo.test"))
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/viagens"));
        assertEquals(List.of("https://app.exemplo.test"), cfg.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"), cfg.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type", "Accept"), cfg.getAllowedHeaders());
        assertFalse(cfg.getAllowCredentials());
        assertEquals(List.of("Retry-After"), cfg.getExposedHeaders());
        assertFalse(cfg.getAllowedOrigins().contains("*"));
    }
}
