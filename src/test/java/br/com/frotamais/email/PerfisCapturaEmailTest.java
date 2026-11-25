package br.com.frotamais.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * A captura local de e-mails só existe no perfil dev: os arquivos de
 * configuração base e prod não mapeiam EMAIL_CAPTURA_LOCAL, então no prod a
 * variável é ignorada mesmo se alguém a definir. Simula o ambiente da
 * hospedagem com as mesmas precedências do Spring (variável de ambiente acima
 * dos arquivos, perfil acima da base).
 */
class PerfisCapturaEmailTest {

    private static Properties arquivo(String nome) throws Exception {
        Properties p = new Properties();
        try (InputStream in = PerfisCapturaEmailTest.class.getResourceAsStream("/" + nome)) {
            p.load(in);
        }
        return p;
    }

    private static StandardEnvironment ambiente(String perfil, Map<String, Object> variaveis) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources fontes = env.getPropertySources();
        fontes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        fontes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        fontes.addLast(new SystemEnvironmentPropertySource("ambiente", variaveis));
        fontes.addLast(new PropertiesPropertySource("perfil", arquivo("application-" + perfil + ".properties")));
        fontes.addLast(new PropertiesPropertySource("base", arquivo("application.properties")));
        return env;
    }

    private static final String CHAVE = "${app.email.captura-local.ativa:false}";

    @Test
    void soOPerfilDevMapeiaACaptura() throws Exception {
        assertTrue(arquivo("application-dev.properties").containsKey("app.email.captura-local.ativa"));
        for (String nome : new String[] {"application.properties", "application-prod.properties"}) {
            Properties p = arquivo(nome);
            assertFalse(p.stringPropertyNames().stream().anyMatch(k -> k.contains("captura")), nome);
            assertFalse(p.values().stream().anyMatch(v -> v.toString().contains("EMAIL_CAPTURA")), nome);
        }
    }

    @Test
    void noProdAVariavelEIgnorada() throws Exception {
        StandardEnvironment prod = ambiente("prod",
                Map.of("EMAIL_CAPTURA_LOCAL", "true", "EMAIL_CAPTURA_DIR", "contexto-local/x"));
        assertEquals("false", prod.resolvePlaceholders(CHAVE));
    }

    @Test
    void noDevAVariavelLigaACaptura() throws Exception {
        assertEquals("true", ambiente("dev", Map.of("EMAIL_CAPTURA_LOCAL", "true")).resolvePlaceholders(CHAVE));
        assertEquals("false", ambiente("dev", Map.of()).resolvePlaceholders(CHAVE));
    }

    @Test
    void forcarAPropriedadeNoProdImpedeASubida() {
        // Mesmo forçando a propriedade (APP_EMAIL_CAPTURALOCAL_ATIVA=true), fora do
        // dev o EmailConfig recusa: ver CapturaLocalEnvioEmailTest#foraDoPerfilDevRecusaSubir.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> EmailConfig.escolher(null, "", true, "contexto-local/emails-dev", false));
    }

    @Test
    void smtpTemTimeoutEmTodosOsPerfis() throws Exception {
        for (String perfil : new String[] {"dev", "prod"}) {
            StandardEnvironment env = ambiente(perfil, Map.of());
            for (String k : new String[] {"connectiontimeout", "timeout", "writetimeout"}) {
                assertEquals("10000", env.getProperty("spring.mail.properties.mail.smtp." + k), perfil + " " + k);
            }
        }
    }

    @Test
    void prodUsaIpRealDoProxyELimiteLigado() throws Exception {
        StandardEnvironment prod = ambiente("prod", Map.of());
        assertEquals("native", prod.getProperty("server.forward-headers-strategy"));
        assertEquals("true", prod.getProperty("app.tentativas.ativo"));
    }
}
