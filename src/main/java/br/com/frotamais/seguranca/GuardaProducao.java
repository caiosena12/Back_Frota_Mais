package br.com.frotamais.seguranca;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Só existe no perfil prod e impede a aplicação de subir com uma configuração
 * perigosa:
 * - perfil dev ativo junto (credenciais locais e captura de e-mails);
 * - banco de teste, de integração ou descartável (pelo nome do banco na
 *   DB_URL), para que a produção nunca grave nesses bancos.
 *
 * - CORS_ORIGENS_PERMITIDAS ausente, sem https ou com curinga (ConfigCors).
 *
 * DB_URL, DB_USER, DB_PASSWORD e JWT_SECRET sem valor já impedem a subida.
 * Nada de valores sensíveis no log: só o nome do banco.
 *
 * É um FlywayConfigurationCustomizer de propósito: o Flyway só é montado
 * depois que esta guarda foi criada (e validou), então nenhuma migration roda
 * num banco proibido antes de a aplicação parar.
 */
@Component
@Profile("prod")
class GuardaProducao implements FlywayConfigurationCustomizer {

    private static final Logger log = LoggerFactory.getLogger(GuardaProducao.class);

    /** Trechos que identificam bancos que não são de produção. */
    static final List<String> BANCOS_PROIBIDOS = List.of("test", "integracao", "descartavel");

    GuardaProducao(Environment ambiente, @Value("${spring.datasource.url:}") String url,
            @Value("${app.cors.origens-permitidas:}") String origensCors) {
        String banco = validar(ambiente.acceptsProfiles(Profiles.of("dev")), url);
        int origens = ConfigCors.origens(origensCors, true).size();
        log.info("Perfil prod ativo; banco '{}'; {} origem(ns) CORS.", banco, origens);
    }

    @Override
    public void customize(FluentConfiguration configuracao) {
        // A validação já ocorreu no construtor; nada a ajustar no Flyway.
    }

    static String validar(boolean perfilDev, String url) {
        if (perfilDev) {
            throw new IllegalStateException("Os perfis prod e dev não podem ficar ativos juntos.");
        }
        String banco = nomeDoBanco(url);
        String minusculo = banco.toLowerCase(Locale.ROOT);
        for (String proibido : BANCOS_PROIBIDOS) {
            if (minusculo.contains(proibido)) {
                throw new IllegalStateException("O perfil prod não pode usar o banco '" + banco
                        + "' (teste, integração ou descartável). Use o banco exclusivo de produção.");
            }
        }
        return banco;
    }

    /** jdbc:postgresql://host:porta/banco?params → banco. */
    static String nomeDoBanco(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DB_URL deve ser uma URL jdbc:postgresql://host:porta/banco.");
        }
        String resto = url.substring("jdbc:postgresql://".length());
        int fimParametros = resto.indexOf('?');
        if (fimParametros >= 0) {
            resto = resto.substring(0, fimParametros);
        }
        int barra = resto.indexOf('/');
        String banco = barra >= 0 ? resto.substring(barra + 1) : "";
        if (banco.isBlank() || banco.contains("/")) {
            throw new IllegalStateException("DB_URL deve indicar o nome do banco: jdbc:postgresql://host:porta/banco.");
        }
        return banco;
    }
}
