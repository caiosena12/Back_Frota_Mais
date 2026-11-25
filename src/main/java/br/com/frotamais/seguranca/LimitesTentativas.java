package br.com.frotamais.seguranca;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.frotamais.comum.TentativasExcedidasException;

/**
 * Limites de tentativas nos endpoints públicos sensíveis (app.tentativas.*):
 *
 * - por IP: login, registro e ativação de convite, contando toda requisição
 *   (LimiteTentativasInterceptor, antes da validação do corpo);
 * - por conta: falhas de login por e-mail normalizado, exista ou não a conta
 *   (mesma resposta nos dois casos), zeradas no login bem-sucedido.
 *
 * O IP é o request.getRemoteAddr(). Atrás de proxy, o perfil prod usa
 * server.forward-headers-strategy=native: o Tomcat só aceita o
 * X-Forwarded-For vindo de proxies confiáveis (internal-proxies) e ignora o
 * que o cliente escreve antes deles. Este código nunca lê o cabeçalho.
 */
@Component
public class LimitesTentativas {

    private final boolean ativo;
    private final JanelaTentativas loginPorIp;
    private final JanelaTentativas loginPorConta;
    private final JanelaTentativas registroPorIp;
    private final JanelaTentativas ativacaoPorIp;

    @Autowired
    public LimitesTentativas(
            @Value("${app.tentativas.ativo:true}") boolean ativo,
            @Value("${app.tentativas.max-chaves:20000}") int maxChaves,
            @Value("${app.tentativas.login-ip.limite:30}") int loginIp,
            @Value("${app.tentativas.login-ip.janela-minutos:5}") long loginIpMin,
            @Value("${app.tentativas.login-conta.limite:10}") int loginConta,
            @Value("${app.tentativas.login-conta.janela-minutos:15}") long loginContaMin,
            @Value("${app.tentativas.registro-ip.limite:10}") int registroIp,
            @Value("${app.tentativas.registro-ip.janela-minutos:60}") long registroIpMin,
            @Value("${app.tentativas.ativacao-ip.limite:20}") int ativacaoIp,
            @Value("${app.tentativas.ativacao-ip.janela-minutos:15}") long ativacaoIpMin) {
        this(ativo, maxChaves, loginIp, loginIpMin, loginConta, loginContaMin, registroIp, registroIpMin,
                ativacaoIp, ativacaoIpMin, Clock.systemUTC());
    }

    LimitesTentativas(boolean ativo, int maxChaves, int loginIp, long loginIpMin, int loginConta, long loginContaMin,
            int registroIp, long registroIpMin, int ativacaoIp, long ativacaoIpMin, Clock relogio) {
        this.ativo = ativo;
        this.loginPorIp = new JanelaTentativas(loginIp, Duration.ofMinutes(loginIpMin), maxChaves, relogio);
        this.loginPorConta = new JanelaTentativas(loginConta, Duration.ofMinutes(loginContaMin), maxChaves, relogio);
        this.registroPorIp = new JanelaTentativas(registroIp, Duration.ofMinutes(registroIpMin), maxChaves, relogio);
        this.ativacaoPorIp = new JanelaTentativas(ativacaoIp, Duration.ofMinutes(ativacaoIpMin), maxChaves, relogio);
    }

    public boolean ativo() {
        return ativo;
    }

    void contarLoginPorIp(String ip) {
        if (ativo) {
            exigir(loginPorIp.registrar(ip));
        }
    }

    void contarRegistroPorIp(String ip) {
        if (ativo) {
            exigir(registroPorIp.registrar(ip));
        }
    }

    void contarAtivacaoPorIp(String ip) {
        if (ativo) {
            exigir(ativacaoPorIp.registrar(ip));
        }
    }

    /** Antes de conferir a senha: conta com falhas demais responde 429, exista ou não. */
    public void exigirContaLiberada(String emailNormalizado) {
        if (ativo) {
            exigir(loginPorConta.bloqueio(emailNormalizado));
        }
    }

    public void registrarFalhaLogin(String emailNormalizado) {
        if (ativo) {
            loginPorConta.registrar(emailNormalizado);
        }
    }

    public void registrarSucessoLogin(String emailNormalizado) {
        if (ativo) {
            loginPorConta.limpar(emailNormalizado);
        }
    }

    private void exigir(long segundos) {
        if (ativo && segundos > 0) {
            throw new TentativasExcedidasException(segundos);
        }
    }
}
