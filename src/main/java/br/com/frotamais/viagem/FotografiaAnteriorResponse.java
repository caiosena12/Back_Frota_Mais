package br.com.frotamais.viagem;

import java.time.Instant;

import br.com.frotamais.usuario.Perfil;
import tools.jackson.databind.JsonNode;

/**
 * Fotografia financeira substituída por uma correção. resultado é a
 * rentabilidade exatamente como estava gravada, no formato daquele momento;
 * resultadoNovo, a que a substituiu (nulo em registros anteriores a esse
 * campo). perfil: o de quem corrigiu, no momento da correção.
 */
public record FotografiaAnteriorResponse(
        Long id,
        Instant substituidaEm,
        Long substituidaPorId,
        String substituidaPorNome,
        Perfil perfil,
        JsonNode resultado,
        JsonNode resultadoNovo) {
}
