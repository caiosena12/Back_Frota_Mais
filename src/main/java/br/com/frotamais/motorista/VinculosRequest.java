package br.com.frotamais.motorista;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lista completa dos caminhões do motorista (substitui a anterior; vazia remove todos). */
public record VinculosRequest(@NotNull @Size(max = 500) List<@NotNull Long> caminhaoIds) {
}
