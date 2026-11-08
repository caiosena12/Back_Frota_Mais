package br.com.frotamais.viagem;

import java.math.BigDecimal;

/** Soma de valores de uma viagem por tipo (TipoCombustivel ou TipoDespesa). */
public record TotalPorTipo(Long viagemId, Enum<?> tipo, BigDecimal valor) {
}
