package br.com.frotamais.viagem;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * Fotografia financeira substituída por uma correção. resultado é a
 * rentabilidade exatamente como estava gravada, no formato daquele momento.
 */
public record FotografiaAnteriorResponse(
        Long id,
        Instant substituidaEm,
        Long substituidaPorId,
        String substituidaPorNome,
        JsonNode resultado) {
}
