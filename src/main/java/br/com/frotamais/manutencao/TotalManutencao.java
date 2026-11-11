package br.com.frotamais.manutencao;

import java.math.BigDecimal;

/** Manutenções não excluídas de um caminhão num período: quantidade e soma dos valores. */
public record TotalManutencao(Long caminhaoId, long quantidade, BigDecimal valor) {
}
