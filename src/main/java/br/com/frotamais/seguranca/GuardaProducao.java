package br.com.frotamais.seguranca;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.sql.DataSource;

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
 * - banco que não seja o de produção explicitamente autorizado.
 *
 * O banco é decidido por lista de permissão, não de bloqueio (fail-closed):
 * - DB_NOME_PRODUCAO (app.producao.banco-autorizado) é obrigatório. Enquanto
 *   não houver banco de produção contratado, o perfil prod não sobe;
 * - o nome autorizado não pode ser de desenvolvimento, teste, integração,
 *   ensaio ou descartável (MARCADORES_NAO_PRODUTIVOS, e nunca "frotamais");
 * - o banco da DB_URL tem de ser exatamente o autorizado, sem codificação (%),
 *   maiúsculas ou parâmetros que troquem o banco (PARAMETROS_PROIBIDOS);
 * - por fim, o próprio PostgreSQL confirma: current_database() tem de ser o
 *   autorizado, o que cobre aliases de pooler e qualquer outro desvio.
 *
 * - CORS_ORIGENS_PERMITIDAS ausente, sem https ou com curinga (ConfigCors);
 * - limite de tentativas desligado (app.tentativas.ativo=false).
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
    static final List<String> MARCADORES_NAO_PRODUTIVOS = List.of("test", "integracao", "descartavel", "dev",
            "local", "missao", "ensaio", "copia", "homolog", "staging", "sandbox", "demo", "exemplo", "tmp",
            "temp", "backup", "bkp");

    /** O banco de desenvolvimento do Codespaces. */
    static final String BANCO_DESENVOLVIMENTO = "frotamais";

    /** Parâmetros do driver que trocariam o banco ou o servidor indicados na URL. */
    static final List<String> PARAMETROS_PROIBIDOS = List.of("pgdbname", "pghost", "pgport", "service", "dbname");

    /** Nome simples do PostgreSQL, em minúsculas: nada de aspas, % ou espaços. */
    private static final Pattern NOME_VALIDO = Pattern.compile("[a-z][a-z0-9_]{2,62}");

    private static final String PREFIXO = "jdbc:postgresql://";

    GuardaProducao(Environment ambiente, DataSource dataSource,
            @Value("${spring.datasource.url:}") String url,
            @Value("${app.producao.banco-autorizado:}") String bancoAutorizado,
            @Value("${app.cors.origens-permitidas:}") String origensCors,
            @Value("${app.tentativas.ativo:true}") boolean limiteTentativas) {
        String banco = validar(ambiente.acceptsProfiles(Profiles.of("dev")), url, bancoAutorizado);
        if (!limiteTentativas) {
            throw new IllegalStateException("O limite de tentativas (app.tentativas.ativo) não pode ser desligado no perfil prod.");
        }
        int origens = ConfigCors.origens(origensCors, true).size();
        confirmarBancoConectado(dataSource, banco);
        log.info("Perfil prod ativo; banco '{}'; {} origem(ns) CORS.", banco, origens);
    }

    @Override
    public void customize(FluentConfiguration configuracao) {
        // A validação já ocorreu no construtor; nada a ajustar no Flyway.
    }

    static String validar(boolean perfilDev, String url, String bancoAutorizado) {
        if (perfilDev) {
            throw new IllegalStateException("Os perfis prod e dev não podem ficar ativos juntos.");
        }
        String autorizado = exigirBancoAutorizado(bancoAutorizado);
        String banco = nomeDoBanco(url);
        if (!banco.equals(autorizado)) {
            throw new IllegalStateException("O perfil prod não pode usar o banco '" + banco
                    + "': só o banco de produção autorizado em DB_NOME_PRODUCAO.");
        }
        return banco;
    }

    /** Nome definido em DB_NOME_PRODUCAO: obrigatório e nunca de outro ambiente. */
    static String exigirBancoAutorizado(String bancoAutorizado) {
        if (bancoAutorizado == null || bancoAutorizado.isBlank()) {
            throw new IllegalStateException("DB_NOME_PRODUCAO não definido: sem um banco de produção autorizado,"
                    + " o perfil prod não sobe.");
        }
        if (!NOME_VALIDO.matcher(bancoAutorizado).matches()) {
            throw new IllegalStateException("DB_NOME_PRODUCAO deve ser um nome simples em minúsculas"
                    + " (letras, números e _).");
        }
        if (naoProdutivo(bancoAutorizado)) {
            throw new IllegalStateException("DB_NOME_PRODUCAO não pode indicar o banco '" + bancoAutorizado
                    + "' (desenvolvimento, teste, integração, ensaio ou descartável).");
        }
        return bancoAutorizado;
    }

    static boolean naoProdutivo(String banco) {
        String minusculo = banco.toLowerCase(Locale.ROOT);
        return minusculo.equals(BANCO_DESENVOLVIMENTO)
                || MARCADORES_NAO_PRODUTIVOS.stream().anyMatch(minusculo::contains);
    }

    /** jdbc:postgresql://host:porta/banco?params → banco. */
    static String nomeDoBanco(String url) {
        if (url == null || !url.startsWith(PREFIXO) || !url.strip().equals(url)) {
            throw new IllegalStateException("DB_URL deve ser uma URL jdbc:postgresql://host:porta/banco.");
        }
        String resto = url.substring(PREFIXO.length());
        int fimParametros = resto.indexOf('?');
        if (fimParametros >= 0) {
            exigirParametrosPermitidos(resto.substring(fimParametros + 1));
            resto = resto.substring(0, fimParametros);
        }
        int barra = resto.indexOf('/');
        String banco = barra >= 0 ? resto.substring(barra + 1) : "";
        if (banco.isBlank()) {
            throw new IllegalStateException("DB_URL deve indicar o nome do banco: jdbc:postgresql://host:porta/banco.");
        }
        if (!NOME_VALIDO.matcher(banco).matches()) {
            throw new IllegalStateException("O banco da DB_URL deve ser um nome simples em minúsculas,"
                    + " sem codificação nem caracteres especiais.");
        }
        return banco;
    }

    private static void exigirParametrosPermitidos(String parametros) {
        for (String parametro : parametros.split("&")) {
            int igual = parametro.indexOf('=');
            String chave = (igual >= 0 ? parametro.substring(0, igual) : parametro).strip().toLowerCase(Locale.ROOT);
            if (chave.contains("%") || PARAMETROS_PROIBIDOS.contains(chave)) {
                throw new IllegalStateException("DB_URL não pode trocar o banco ou o servidor por parâmetro ("
                        + String.join(", ", PARAMETROS_PROIBIDOS) + ").");
            }
        }
    }

    /** A palavra final é do PostgreSQL: a conexão tem de chegar ao banco autorizado. */
    static void confirmarBancoConectado(DataSource dataSource, String bancoAutorizado) {
        String conectado;
        try (Connection conexao = dataSource.getConnection();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("SELECT current_database()")) {
            conectado = resultado.next() ? resultado.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível confirmar o banco de produção antes das migrations.", e);
        }
        if (!bancoAutorizado.equals(conectado)) {
            throw new IllegalStateException("A conexão chegou ao banco '" + conectado
                    + "', não ao banco de produção autorizado '" + bancoAutorizado + "'.");
        }
    }
}
