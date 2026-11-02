package br.com.frotamais.suporte;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class GuardaBancoDeTesteTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void conexaoEfetivaDaSuiteEODoBancoDeTeste() throws Exception {
        try (Connection conexao = dataSource.getConnection();
                Statement comando = conexao.createStatement();
                ResultSet resultado = comando.executeQuery("SELECT current_database()")) {
            assertTrue(resultado.next());
            assertEquals("frotamais_test", resultado.getString(1));
        }
    }

    @Test
    void guardaRecusaBancoDeDesenvolvimento() {
        IllegalStateException erro = assertThrows(
                IllegalStateException.class,
                () -> GuardaBancoDeTeste.exigirBancoDeTeste("frotamais"));

        assertTrue(erro.getMessage().contains("frotamais_test"));
    }

    @Test
    void guardaAceitaBancoDeTeste() {
        assertDoesNotThrow(
                () -> GuardaBancoDeTeste.exigirBancoDeTeste("frotamais_test"));
    }
}
