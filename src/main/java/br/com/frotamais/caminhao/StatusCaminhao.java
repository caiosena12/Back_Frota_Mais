package br.com.frotamais.caminhao;

public enum StatusCaminhao {
    /** Disponível para novas viagens. */
    ATIVO,
    /** Temporariamente fora de operação: consultável, sem novas viagens. */
    INATIVO,
    /** Removido da operação (ex.: venda). Exclusão lógica: histórico preservado. */
    EXCLUIDO
}
