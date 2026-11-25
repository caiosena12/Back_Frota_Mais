package br.com.frotamais.comum;

import org.springframework.http.HttpStatus;

/**
 * 429: limite de tentativas excedido (login, registro, ativação de convite).
 * O ApiExceptionHandler devolve o corpo padrão e o cabeçalho Retry-After com
 * os segundos até a janela liberar. A mensagem é a mesma em todos os casos,
 * para não revelar se um e-mail existe.
 */
public class TentativasExcedidasException extends RegraNegocioException {

    public static final String MENSAGEM = "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente.";

    private final long segundosParaLiberar;

    public TentativasExcedidasException(long segundosParaLiberar) {
        super(HttpStatus.TOO_MANY_REQUESTS, MENSAGEM);
        this.segundosParaLiberar = Math.max(1, segundosParaLiberar);
    }

    public long getSegundosParaLiberar() {
        return segundosParaLiberar;
    }
}
