package br.com.frotamais.motorista;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro do motorista pelo dono: nome, e-mail e caminhões a que terá acesso. Sem senha. */
public record MotoristaRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 500) List<@NotNull Long> caminhaoIds) {

    List<Long> caminhaoIdsOuVazio() {
        return caminhaoIds == null ? List.of() : caminhaoIds;
    }
}
