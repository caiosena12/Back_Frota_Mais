package br.com.frotamais.usuario;

import java.nio.charset.StandardCharsets;

import br.com.frotamais.comum.RegraNegocioException;

/**
 * Regras para toda senha nova (cadastro do dono, ativação do convite e troca
 * de senha): de 8 caracteres a 72 bytes (limite do BCrypt, acima do qual o
 * restante seria ignorado em silêncio), com pelo menos uma letra e um número.
 */
public final class PoliticaSenha {

    public static final int MINIMO_CARACTERES = 8;
    public static final int MAXIMO_BYTES = 72;

    private PoliticaSenha() {
    }

    public static void validarNova(String senha) {
        if (senha == null || senha.length() < MINIMO_CARACTERES) {
            throw RegraNegocioException.invalido("A senha deve ter pelo menos " + MINIMO_CARACTERES + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw RegraNegocioException.invalido("A senha ultrapassa o limite permitido pelo BCrypt.");
        }
        if (senha.chars().noneMatch(Character::isLetter) || senha.chars().noneMatch(Character::isDigit)) {
            throw RegraNegocioException.invalido("A senha deve ter pelo menos uma letra e um número.");
        }
    }
}
