package br.com.frotamais.comum;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Fuso do produto e relógio: "hoje" e "agora" sempre no mesmo critério. */
public final class Datas {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private Datas() {
    }

    public static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /** Instante em microssegundos, a precisão do PostgreSQL: o valor não muda ao recarregar. */
    public static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
