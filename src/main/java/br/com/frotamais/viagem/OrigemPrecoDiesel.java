package br.com.frotamais.viagem;

/** De onde veio o preço médio do diesel usado no cálculo do frete. */
public enum OrigemPrecoDiesel {
    /** Média ponderada dos abastecimentos de diesel da própria viagem. */
    ABASTECIMENTOS_DA_VIAGEM,
    /** Média ponderada da viagem anterior mais recente do mesmo caminhão com diesel. */
    ULTIMO_PRECO_DO_CAMINHAO,
    /** Sem abastecimento na viagem nem histórico: o custo do diesel não é estimado. */
    SEM_REFERENCIA
}
