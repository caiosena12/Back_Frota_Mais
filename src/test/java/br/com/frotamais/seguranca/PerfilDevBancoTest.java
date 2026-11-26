package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * O perfil dev não escolhe banco sozinho: sem DB_URL a aplicação não sobe, em
 * vez de cair em silêncio no banco persistente "frotamais". Mesmas precedências
 * do Spring (variável de ambiente, depois perfil, depois base).
 */
class PerfilDevBancoTest {

    private static Properties arquivo(String nome) throws Exception {
        Properties p = new Properties();
        try (InputStream in = PerfilDevBancoTest.class.getResourceAsStream("/" + nome)) {
            p.load(in);
        }
        return p;
    }

    private static StandardEnvironment dev(Map<String, Object> variaveis) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        MutablePropertySources fontes = env.getPropertySources();
        fontes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        fontes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        fontes.addLast(new SystemEnvironmentPropertySource("ambiente", variaveis));
        fontes.addLast(new PropertiesPropertySource("perfil", arquivo("application-dev.properties")));
        fontes.addLast(new PropertiesPropertySource("base", arquivo("application.properties")));
        return env;
    }

    @Test
    void semDbUrlODevNaoTemBanco() throws Exception {
        StandardEnvironment env = dev(Map.of());
        assertThrows(IllegalArgumentException.class, () -> env.getProperty("spring.datasource.url"));
    }

    @Test
    void comDbUrlODevUsaOBancoInformado() throws Exception {
        String url = "jdbc:postgresql://localhost:5432/frotamais_dev_local";
        assertEquals(url, dev(Map.of("DB_URL", url)).getProperty("spring.datasource.url"));
    }

    @Test
    void nenhumArquivoPrincipalApontaParaUmBancoPorPadrao() throws Exception {
        for (String nome : new String[] {"application.properties", "application-dev.properties",
                "application-prod.properties"}) {
            Properties p = arquivo(nome);
            assertFalse(p.values().stream().anyMatch(v -> v.toString().contains("jdbc:postgresql")), nome);
        }
    }
}
