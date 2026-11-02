package br.com.frotamais.suporte;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Impede que um teste rode fora do banco exclusivo de testes.
 *
 * O arquivo config/application.properties escolhe o banco, mas variáveis como
 * SPRING_DATASOURCE_URL têm precedência sobre ele. Por isso a decisão final é
 * do próprio PostgreSQL: assim que o DataSource é criado, antes de o Hibernate
 * aplicar o ddl-auto, perguntamos a qual banco a conexão chegou.
 *
 * Registrado em META-INF/spring.factories, vale para todo contexto Spring da
 * suíte, sem anotação, e só existe no classpath de teste.
 */
public class GuardaBancoDeTeste
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String BANCO_ESPERADO = "frotamais_test";

    private static final Logger log = LoggerFactory.getLogger(GuardaBancoDeTeste.class);

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        contexto.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nome) {
                if (bean instanceof DataSource dataSource) {
                    confirmarBanco(dataSource);
                }
                return bean;
            }
        });
    }

    private static void confirmarBanco(DataSource dataSource) {
        String banco = consultarBanco(dataSource);
        exigirBancoDeTeste(banco);
        log.info("Banco de testes confirmado: {}", banco);
    }

    private static String consultarBanco(DataSource dataSource) {
        try (Connection conexao = dataSource.getConnection();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("SELECT current_database()")) {
            resultado.next();
            return resultado.getString(1);
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException(
                    "Não foi possível conectar ao banco de testes '" + BANCO_ESPERADO
                            + "'. Confira se o PostgreSQL está ligado e se o banco existe"
                            + " (CREATE DATABASE " + BANCO_ESPERADO + "). Motivo: "
                            + e.getMessage(),
                    e);
        }
    }

    static void exigirBancoDeTeste(String banco) {
        if (!BANCO_ESPERADO.equals(banco)) {
            throw new IllegalStateException(
                    "Testes bloqueados: conectados ao banco '" + banco
                            + "', mas só podem usar '" + BANCO_ESPERADO + "'.");
        }
    }
}
