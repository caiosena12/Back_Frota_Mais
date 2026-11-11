package br.com.frotamais.viagem;

public enum StatusViagem {
    EM_ANDAMENTO,
    FINALIZADA,
    /** Encerrada sem conclusão do frete, pelo DONO, com motivo. Preserva lançamentos e resultado. */
    CANCELADA
}
