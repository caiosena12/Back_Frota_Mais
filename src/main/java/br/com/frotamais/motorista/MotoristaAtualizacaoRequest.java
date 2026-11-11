package br.com.frotamais.motorista;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT = substituição integral destes campos. O e-mail não muda aqui: ele é a
 * identidade de login. ativo=false desativa; ativo=true reativa. Conta com
 * convite pendente só é ativada pelo próprio motorista, pelo convite.
 */
public record MotoristaAtualizacaoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull Boolean ativo) {
}
