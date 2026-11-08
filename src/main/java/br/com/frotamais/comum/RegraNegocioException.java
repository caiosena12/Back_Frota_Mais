package br.com.frotamais.comum;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio com mensagem destinada ao cliente.
 *
 * Diferente de ResponseStatusException, que passa pelo /error do Spring e
 * perde a mensagem (include-message=never), esta exceção é tratada pelo
 * ApiExceptionHandler e devolve o motivo no corpo, no mesmo formato da
 * validação: {"status", "erro", "detalhes"}.
 */
public class RegraNegocioException extends RuntimeException {

    private final HttpStatus status;

    public RegraNegocioException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public static RegraNegocioException invalido(String mensagem) {
        return new RegraNegocioException(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static RegraNegocioException conflito(String mensagem) {
        return new RegraNegocioException(HttpStatus.CONFLICT, mensagem);
    }

    public static RegraNegocioException naoEncontrado(String mensagem) {
        return new RegraNegocioException(HttpStatus.NOT_FOUND, mensagem);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
