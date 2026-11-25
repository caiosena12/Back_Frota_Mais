package br.com.frotamais.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Guarda do perfil prod, sem subir o contexto. */
class GuardaProducaoTest {

    @Test
    void aceitaBancoDeProducao() {
        assertEquals("frotamais_producao",
                GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test:5432/frotamais_producao?sslmode=require"));
        assertEquals("frotamais", GuardaProducao.validar(false, "jdbc:postgresql://db.exemplo.test/frotamais"));
    }

    @Test
    void recusaBancosDeTesteIntegracaoEDescartaveis() {
        for (String banco : List.of("frotamais_test_flyway", "frotamais_test", "frotamais_integracao",
                "frotamais_descartavel_v11", "FROTAMAIS_INTEGRACAO")) {
            assertThrows(IllegalStateException.class,
                    () -> GuardaProducao.validar(false, "jdbc:postgresql://localhost:5432/" + banco), banco);
        }
    }

    @Test
    void recusaPerfilDevJunto() {
        assertThrows(IllegalStateException.class,
                () -> GuardaProducao.validar(true, "jdbc:postgresql://db.exemplo.test/frotamais_producao"));
    }

    @Test
    void recusaUrlSemBancoOuDeOutroTipo() {
        for (String url : java.util.Arrays.asList(null, "", "jdbc:h2:mem:x", "jdbc:postgresql://host:5432",
                "jdbc:postgresql://host:5432/", "jdbc:postgresql://host:5432/?ssl=true")) {
            assertThrows(IllegalStateException.class, () -> GuardaProducao.validar(false, url), String.valueOf(url));
        }
    }
}
