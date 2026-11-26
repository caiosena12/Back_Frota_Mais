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

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
