package br.com.frotamais.comum;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio com mensagem destinada ao cliente.
 *
 * Tratada pelo ApiExceptionHandler, que devolve o motivo no corpo no formato
 * único de erro: {"status", "erro", "detalhes"} (ver RespostaErro).
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

    public static RegraNegocioException naoAutenticado(String mensagem) {
        return new RegraNegocioException(HttpStatus.UNAUTHORIZED, mensagem);
    }

    public static RegraNegocioException proibido(String mensagem) {
        return new RegraNegocioException(HttpStatus.FORBIDDEN, mensagem);
    }

    public static RegraNegocioException indisponivel(String mensagem) {
        return new RegraNegocioException(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
    }

    public static RegraNegocioException naoEncontrado(String mensagem) {
        return new RegraNegocioException(HttpStatus.NOT_FOUND, mensagem);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
