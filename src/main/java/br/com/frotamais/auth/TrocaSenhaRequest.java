package br.com.frotamais.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequest(
        @NotBlank @Size(max = 72) String senhaAtual,
        @NotBlank @Size(max = 72) String novaSenha) {
}
