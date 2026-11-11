package br.com.frotamais.convite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtivacaoRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(max = 72) String senha) {
}
