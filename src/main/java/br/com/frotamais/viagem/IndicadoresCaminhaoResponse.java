package br.com.frotamais.viagem;

/** Indicadores de um caminhão no período (mesmas regras de IndicadoresResponse). */
public record IndicadoresCaminhaoResponse(Long caminhaoId, String placa, IndicadoresResponse indicadores) {
}
