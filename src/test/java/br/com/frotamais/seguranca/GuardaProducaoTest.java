package br.com.frotamais.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** Guarda do perfil prod, sem banco real: URLs avulsas e DataSource simulado. */
class GuardaProducaoTest {

    private static final String AUTORIZADO = "frotamais_producao";

    private static final String URL_AUTORIZADA = "jdbc:postgresql://db.exemplo.test:5432/" + AUTORIZADO;

    @Test
    void aceitaSoOBancoDeProducaoAutorizado() {
        assertEquals(AUTORIZADO, GuardaProducao.validar(false, URL_AUTORIZADA + "?sslmode=require", AUTORIZADO));
        assertEquals(AUTORIZADO, GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/" + AUTORIZADO, AUTORIZADO));
        assertEquals("frotamais_prd", GuardaProducao.validar(false,
                "jdbc:postgresql://a.exemplo.test:5432,b.exemplo.test:5432/frotamais_prd?ssl=true&sslmode=verify-full",
                "frotamais_prd"));
    }

    @Test
    void recusaBancosConhecidosDeOutrosAmbientesMesmoSeAutorizados() {
        for (String banco : List.of("frotamais", "frotamais_integracao", "frotamais_test_flyway", "frotamais_test",
                "frotamais_missao_lancamentos", "frotamais_missao_banco_novo", "frotamais_missao_final",
                "frotamais_integracao_v12", "frotamais_dev_local", "frotamais_descartavel_v11", "frotamais_copia",
                "frotamais_homologacao", "frotamais_teste")) {
            String url = "jdbc:postgresql://localhost:5432/" + banco;
            // Autorizar o próprio nome não adianta: o nome autorizado também é conferido.
            assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(false, url, banco), banco);
            assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(false, url, AUTORIZADO), banco);
        }
    }

    @Test
    void recusaBancoNaoIdentificadoQueNaoSejaOAutorizado() {
        assertThrows(IllegalStateException.class,
                () -> GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/clientes", AUTORIZADO));
        assertThrows(IllegalStateException.class,
                () -> GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/frotamais_producao2", AUTORIZADO));
        assertThrows(IllegalStateException.class,
                () -> GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/postgres", AUTORIZADO));
    }

    @Test
    void recusaConfiguracaoSemBancoAutorizado() {
        for (String autorizado : Arrays.asList(null, "", "   ")) {
            assertThrows(IllegalStateException.class,
                    () -> GuardaProducao.validar(false, URL_AUTORIZADA, autorizado), String.valueOf(autorizado));
        }
    }

    @Test
    void recusaNomeAutorizadoForaDoPadrao() {
        for (String autorizado : List.of("Frotamais_Producao", "frotamais producao", "frotamais-producao",
                "frotamais%5Fproducao", "\"frotamais_producao\"", "fp", "1frotamais")) {
            assertThrows(IllegalStateException.class,
                    () -> GuardaProducao.exigirBancoAutorizado(autorizado), autorizado);
        }
    }

    @Test
    void recusaTentativasDeContornarOCasamentoDoNome() {
        for (String url : List.of(
                // maiúsculas: o PostgreSQL diferencia, e só o nome exato vale
                "jdbc:postgresql://db.exemplo.test/FROTAMAIS_PRODUCAO",
                // codificação: o driver decodificaria %5F para _
                "jdbc:postgresql://db.exemplo.test/frotamais%5Fproducao",
                // parâmetros do driver que trocam o banco ou o servidor
                URL_AUTORIZADA + "?PGDBNAME=frotamais",
                URL_AUTORIZADA + "?sslmode=require&pgdbname=frotamais",
                URL_AUTORIZADA + "?PGHOST=localhost",
                URL_AUTORIZADA + "?PGPORT=5433",
                URL_AUTORIZADA + "?service=dev",
                URL_AUTORIZADA + "?%50GDBNAME=frotamais",
                // caminho extra, espaços e prefixos diferentes
                URL_AUTORIZADA + "/outro",
                " " + URL_AUTORIZADA,
                URL_AUTORIZADA + "\n",
                "JDBC:POSTGRESQL://db.exemplo.test/" + AUTORIZADO,
                "jdbc:postgresql:" + AUTORIZADO,
                "jdbc:postgresql:///" + AUTORIZADO + "x")) {
            assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(false, url, AUTORIZADO), url);
        }
    }

    @Test
    void recusaPerfilDevJunto() {
        assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(true, URL_AUTORIZADA, AUTORIZADO));
    }

    @Test
    void recusaUrlAusenteSemBancoOuDeOutroTipo() {
        for (String url : Arrays.asList(null, "", "jdbc:h2:mem:x", "jdbc:postgresql://host:5432",
                "jdbc:postgresql://host:5432/", "jdbc:postgresql://host:5432/?ssl=true")) {
            assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(false, url, AUTORIZADO),
                    String.valueOf(url));
        }
    }

    @Test
    void exigeQueOPostgresConfirmeOBancoAutorizado() throws SQLException {
        assertDoesNotThrow(() -> GuardaProducao.confirmarBancoConectado(dataSourceConectadoA(AUTORIZADO), AUTORIZADO));
        // Um alias de pooler ou um redirecionamento que chega a outro banco é recusado.
        for (String conectado : Arrays.asList("frotamais", "frotamais_dev_local", null)) {
            DataSource dataSource = dataSourceConectadoA(conectado);
            assertThrows(IllegalStateException.class,
                    () -> GuardaProducao.confirmarBancoConectado(dataSource, AUTORIZADO), String.valueOf(conectado));
        }
        DataSource indisponivel = mock(DataSource.class);
        when(indisponivel.getConnection()).thenThrow(new SQLException("sem conexão"));
        assertThrows(IllegalStateException.class,
                () -> GuardaProducao.confirmarBancoConectado(indisponivel, AUTORIZADO));
    }

    @Test
    void configuracaoInvalidaImpedeASubidaAntesDoFlyway() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withUserConfiguration(GuardaProducao.class)
                .withPropertyValues("spring.profiles.active=prod",
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/frotamais_dev_local",
                        "app.producao.banco-autorizado=" + AUTORIZADO,
                        "app.cors.origens-permitidas=https://app.exemplo.test")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("frotamais_dev_local");
                });
        // Nem a guarda nem o Flyway chegaram a abrir uma conexão.
        verify(dataSource, never()).getConnection();
    }

    @Test
    void semBancoAutorizadoOPerfilProdNaoSobe() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withUserConfiguration(GuardaProducao.class)
                .withPropertyValues("spring.profiles.active=prod", "spring.datasource.url=" + URL_AUTORIZADA,
                        "app.cors.origens-permitidas=https://app.exemplo.test")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining("DB_NOME_PRODUCAO");
                });
        verify(dataSource, never()).getConnection();
    }

    // ===== Flyway na mesma conexão validada (desvio por SPRING_FLYWAY_URL, 26/11/2026) =====

    @Test
    void qaEHmlComoTermoProprioSaoNaoProdutivos() {
        for (String banco : List.of("frotamais_qa", "frotamais_hml", "qa_frotamais", "hml_frotamais",
                "frotamais-hml", "frotamais-qa", "FROTAMAIS_QA", "frotamais_qa2", "frotamais_hml_v12",
                "frotamais_cloudrun_qa", "qa", "hml")) {
            assertThat(GuardaProducao.naoProdutivo(banco)).as(banco).isTrue();
        }
        for (String banco : List.of("frotamais_qa", "frotamais_hml", "qa_frotamais", "frotamais_cloudrun_qa")) {
            assertThrows(IllegalStateException.class, () -> GuardaProducao.exigirBancoAutorizado(banco), banco);
            assertThrows(IllegalStateException.class,
                    () -> GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/" + banco, banco), banco);
        }
        // Siglas dentro de outra palavra não contam (sem falso positivo por coincidência de letras).
        for (String banco : List.of(AUTORIZADO, "frotamais_iqaluit", "frotamais_ohmlab", "frotamais_prd")) {
            assertThat(GuardaProducao.naoProdutivo(banco)).as(banco).isFalse();
        }
    }

    @Test
    void cenarioA_flywayUsaAMesmaConexaoValidadaEMigra() throws SQLException {
        DataSource validado = dataSourceConectadoA(AUTORIZADO);
        runnerProdValido(validado)
                // Substitui só a execução real das migrations (o DataSource é simulado);
                // a estratégia da guarda é exercida abaixo, sobre o Flyway montado pelo Boot.
                .withBean(FlywayMigrationInitializer.class, () -> new FlywayMigrationInitializer(mock(Flyway.class)))
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    Flyway montado = contexto.getBean(Flyway.class);
                    assertThat(montado.getConfiguration().getDataSource()).isSameAs(validado);
                    assertThat(contexto.getBean(FlywayMigrationStrategy.class)).isInstanceOf(GuardaProducao.class);
                });
        GuardaProducao guarda = guardaValida(validado);
        Flyway flyway = flywayCom(validado);
        guarda.migrate(flyway);
        verify(flyway).migrate();
    }

    @Test
    void cenarioB_flywayUrlParaOutroBancoNaoSobeNemConecta() throws SQLException {
        for (String outro : List.of("frotamais_outro", "frotamais_producao_copia2", AUTORIZADO)) {
            DataSource validado = dataSourceConectadoA(AUTORIZADO);
            // Até a URL do próprio banco autorizado é recusada: o Flyway não tem conexão própria no prod.
            runnerProdValido(validado)
                    .withPropertyValues("spring.flyway.url=jdbc:postgresql://alternativo.invalid:5432/" + outro)
                    .run(contexto -> assertRecusaDaGuarda(contexto, "spring.flyway.url"));
            verify(validado, never()).getConnection();
        }
    }

    @Test
    void cenarioC_flywayApontandoParaBancoDeQaOuHmlNaoSobe() throws SQLException {
        for (String qa : List.of("frotamais_qa", "frotamais_hml", "qa_frotamais")) {
            DataSource validado = dataSourceConectadoA(AUTORIZADO);
            runnerProdValido(validado)
                    .withPropertyValues("spring.flyway.url=jdbc:postgresql://alternativo.invalid:5432/" + qa,
                            "spring.flyway.user=qa", "spring.flyway.password=qa")
                    .run(contexto -> assertRecusaDaGuarda(contexto, "spring.flyway."));
            verify(validado, never()).getConnection();
        }
    }

    @Test
    void cenarioD_semConfiguracaoObrigatoriaOuIdentidadeNaoVerificavel() throws SQLException {
        DataSource semUrl = mock(DataSource.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> semUrl)
                .withUserConfiguration(GuardaProducao.class)
                .withPropertyValues("spring.profiles.active=prod", "app.producao.banco-autorizado=" + AUTORIZADO,
                        "app.cors.origens-permitidas=https://app.exemplo.test")
                .run(contexto -> assertRecusaDaGuarda(contexto, "DB_URL"));
        verify(semUrl, never()).getConnection();

        DataSource indisponivel = mock(DataSource.class);
        when(indisponivel.getConnection()).thenThrow(new SQLException("sem conexão"));
        runnerProdValido(indisponivel).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).hasStackTraceContaining("Não foi possível confirmar o banco");
        });

        DataSource outroBanco = dataSourceConectadoA("frotamais_outro");
        runnerProdValido(outroBanco).run(contexto -> assertRecusaDaGuarda(contexto, "frotamais_outro"));
    }

    @Test
    void cenarioE_todasAsFormasDeConexaoPropriaDoFlywaySaoRecusadas() throws SQLException {
        String url = "jdbc:postgresql://alternativo.invalid:5432/frotamais_outro";
        List<Map<String, Object>> formas = List.of(
                // variáveis de ambiente, como no Cloud Run
                Map.of("SPRING_FLYWAY_URL", url),
                Map.of("SPRING_FLYWAY_USER", "outro"),
                Map.of("SPRING_FLYWAY_PASSWORD", "outra"),
                Map.of("SPRING_FLYWAY_DRIVER_CLASS_NAME", "org.postgresql.Driver"),
                Map.of("SPRING_FLYWAY_URL", url, "SPRING_FLYWAY_USER", "outro", "SPRING_FLYWAY_PASSWORD", "outra"),
                // propriedades, nas grafias aceitas pelo Spring
                Map.of("spring.flyway.url", url),
                Map.of("spring.flyway.user", "outro"),
                Map.of("spring.flyway.password", "outra"),
                Map.of("spring.flyway.driverClassName", "org.postgresql.Driver"),
                Map.of("spring.flyway.url", ""));
        for (Map<String, Object> forma : formas) {
            DataSource validado = dataSourceConectadoA(AUTORIZADO);
            runnerProdValido(validado)
                    .withInitializer(contexto -> contexto.getEnvironment().getPropertySources()
                            .addFirst(new SystemEnvironmentPropertySource("ambiente-teste", forma)))
                    .run(contexto -> assertRecusaDaGuarda(contexto, "conexão própria do Flyway"));
            verify(validado, never()).getConnection();
        }
        // Grafia que o Spring não reconhece (nem a guarda nem o Boot a leem): o Flyway segue na conexão validada.
        DataSource validado = dataSourceConectadoA(AUTORIZADO);
        runnerProdValido(validado)
                .withInitializer(contexto -> contexto.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("ambiente-teste",
                                Map.of("SPRING_FLYWAY_DRIVERCLASSNAME", "org.postgresql.Driver"))))
                .withBean(FlywayMigrationInitializer.class, () -> new FlywayMigrationInitializer(mock(Flyway.class)))
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto.getBean(Flyway.class).getConfiguration().getDataSource()).isSameAs(validado);
                });
    }

    @Test
    void cenarioE_flywayDataSourceAlternativoNuncaEAberto() throws SQLException {
        DataSource validado = dataSourceConectadoA(AUTORIZADO);
        DataSource alternativo = dataSourceConectadoA(AUTORIZADO);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean("principal", DataSource.class, () -> validado, bd -> bd.setPrimary(true))
                .withUserConfiguration(FlywayComDataSourceProprio.class)
                .withBean("alternativoDoFlyway", DataSource.class, () -> alternativo)
                .withUserConfiguration(GuardaProducao.class)
                .withPropertyValues(propriedadesProdValidas())
                .run(contexto -> assertRecusaDaGuarda(contexto, "conexão diferente da validada"));
        // Mesmo dizendo ser o banco autorizado, o alternativo nem chega a ser aberto.
        verify(alternativo, never()).getConnection();
    }

    @Test
    void cenarioE_customizerQueTrocaAConexaoNaoEscapaDaBarreiraFinal() throws SQLException {
        DataSource validado = dataSourceConectadoA(AUTORIZADO);
        DataSource alternativo = dataSourceConectadoA(AUTORIZADO);
        // Rode antes ou depois da guarda, a troca é barrada (no customize ou no migrate).
        runnerProdValido(validado)
                .withBean("trocaConexao", FlywayConfigurationCustomizer.class,
                        () -> configuracao -> configuracao.dataSource(alternativo))
                .run(contexto -> assertRecusaDaGuarda(contexto, "conexão diferente da validada"));
        verify(alternativo, never()).getConnection();

        // Direto na barreira final: Flyway já montado com outra conexão → nada de migrate.
        GuardaProducao guarda = guardaValida(dataSourceConectadoA(AUTORIZADO));
        Flyway flyway = flywayCom(alternativo);
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> guarda.migrate(flyway));
        assertThat(erro).hasMessageContaining("conexão diferente da validada");
        verify(flyway, never()).migrate();
        verify(alternativo, never()).getConnection();
    }

    @Test
    void barreiraFinalReconfirmaOBancoNoPostgres() throws SQLException {
        // Mesmo objeto, mas o banco mudou depois da subida (ex.: alias de pooler): sem migrate.
        DataSource validado = mock(DataSource.class);
        Connection conexao = mock(Connection.class);
        Statement comando = mock(Statement.class);
        ResultSet primeiro = mock(ResultSet.class);
        ResultSet segundo = mock(ResultSet.class);
        when(validado.getConnection()).thenReturn(conexao);
        when(conexao.createStatement()).thenReturn(comando);
        when(comando.executeQuery("SELECT current_database()")).thenReturn(primeiro, segundo);
        when(primeiro.next()).thenReturn(true);
        when(primeiro.getString(1)).thenReturn(AUTORIZADO);
        when(segundo.next()).thenReturn(true);
        when(segundo.getString(1)).thenReturn("frotamais_qa");
        GuardaProducao guarda = guardaValida(validado);
        Flyway flyway = flywayCom(validado);
        assertThrows(IllegalStateException.class, () -> guarda.migrate(flyway));
        verify(flyway, never()).migrate();
    }

    /** Declara um DataSource exclusivo do Flyway, como faria um @FlywayDataSource. */
    @Configuration(proxyBeanMethods = false)
    static class FlywayComDataSourceProprio {
        @Bean
        @FlywayDataSource
        DataSource dataSourceDoFlyway(org.springframework.beans.factory.BeanFactory fabrica) {
            return fabrica.getBean("alternativoDoFlyway", DataSource.class);
        }
    }

    private static String[] propriedadesProdValidas() {
        return new String[] { "spring.profiles.active=prod", "spring.datasource.url=" + URL_AUTORIZADA,
                "app.producao.banco-autorizado=" + AUTORIZADO, "app.cors.origens-permitidas=https://app.exemplo.test" };
    }

    private static ApplicationContextRunner runnerProdValido(DataSource dataSource) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withBean(DataSource.class, () -> dataSource)
                .withUserConfiguration(GuardaProducao.class)
                .withPropertyValues(propriedadesProdValidas());
    }

    private static void assertRecusaDaGuarda(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext contexto, String trecho) {
        assertThat(contexto).hasFailed();
        assertThat(contexto.getStartupFailure()).rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(trecho);
    }

    private static GuardaProducao guardaValida(DataSource dataSource) {
        return new GuardaProducao(new MockEnvironment(), dataSource, URL_AUTORIZADA, AUTORIZADO,
                "https://app.exemplo.test", true);
    }

    private static Flyway flywayCom(DataSource dataSource) {
        Flyway flyway = mock(Flyway.class);
        org.flywaydb.core.api.configuration.Configuration configuracao =
                mock(org.flywaydb.core.api.configuration.Configuration.class);
        when(flyway.getConfiguration()).thenReturn(configuracao);
        when(configuracao.getDataSource()).thenReturn(dataSource);
        return flyway;
    }

    private static DataSource dataSourceConectadoA(String banco) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection conexao = mock(Connection.class);
        Statement comando = mock(Statement.class);
        ResultSet resultado = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.createStatement()).thenReturn(comando);
        when(comando.executeQuery("SELECT current_database()")).thenReturn(resultado);
        when(resultado.next()).thenReturn(true);
        when(resultado.getString(1)).thenReturn(banco);
        return dataSource;
    }
}
