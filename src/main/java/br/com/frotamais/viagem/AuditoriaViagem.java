package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.UsuarioRepository;
import br.com.frotamais.viagem.AlteracaoViagem.Entidade;
import tools.jackson.databind.ObjectMapper;

/**
 * Trilha de auditoria das correções de viagens encerradas (finalizadas ou
 * canceladas), que só o DONO faz.
 *
 * Uso: tirar um retrato dos campos antes da correção (campos(...)), aplicar a
 * correção e chamar registrar(...) com o retrato de depois. Só os campos que
 * mudaram são gravados, um registro por campo. A fotografia financeira
 * substituída é guardada por arquivarFotografia(...), antes de ser regravada.
 *
 * Valores são guardados como texto: datas em ISO (2025-06-01), números sem
 * notação científica (BigDecimal.toPlainString), ausência como nulo.
 */
@Component
public class AuditoriaViagem {

    private final AlteracaoViagemRepository alteracoes;
    private final FotografiaAnteriorRepository fotografias;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;
    private final ObjectMapper json;

    public AuditoriaViagem(AlteracaoViagemRepository alteracoes, FotografiaAnteriorRepository fotografias,
            UsuarioRepository usuarios, UsuarioLogado logado, ObjectMapper json) {
        this.alteracoes = alteracoes;
        this.fotografias = fotografias;
        this.usuarios = usuarios;
        this.logado = logado;
        this.json = json;
    }

    /** Grava um registro para cada campo cujo valor mudou entre antes e depois. */
    public void registrar(Viagem viagem, Entidade entidade, Long registroId,
            Map<String, String> antes, Map<String, String> depois) {
        Instant agora = agora();
        for (Map.Entry<String, String> campo : depois.entrySet()) {
            String anterior = antes.get(campo.getKey());
            if (!Objects.equals(anterior, campo.getValue())) {
                alteracoes.save(new AlteracaoViagem(viagem, usuarios.getReferenceById(logado.usuarioId()), agora,
                        entidade, registroId, campo.getKey(), anterior, campo.getValue()));
            }
        }
    }

    /** Guarda a fotografia atual (se houver) antes de uma correção regravá-la. */
    public void arquivarFotografia(Viagem viagem) {
        RentabilidadeResponse atual = viagem.resultadoConsolidado();
        if (atual != null) {
            fotografias.save(new FotografiaAnterior(viagem, agora(),
                    usuarios.getReferenceById(logado.usuarioId()), json.writeValueAsString(atual)));
        }
    }

    static Map<String, String> campos(Viagem v) {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("dataInicio", texto(v.getDataInicio()));
        c.put("dataFim", texto(v.getDataFim()));
        c.put("valorFrete", texto(v.getValorFrete()));
        c.put("origem", v.getOrigem());
        c.put("destino", v.getDestino());
        c.put("kmInicialVazio", texto(v.getKmInicialVazio()));
        c.put("kmInicialCarregado", texto(v.getKmInicialCarregado()));
        c.put("kmFinalCarregado", texto(v.getKmFinalCarregado()));
        c.put("consumoMedioVazio", texto(v.getConsumoMedioVazio()));
        c.put("consumoMedioCarregado", texto(v.getConsumoMedioCarregado()));
        c.put("motoristaId", v.getMotorista() != null ? texto(v.getMotorista().getId()) : null);
        return c;
    }

    static Map<String, String> campos(Abastecimento a) {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("tipo", texto(a.getTipo()));
        c.put("data", texto(a.getData()));
        c.put("hora", texto(a.getHora()));
        c.put("litros", texto(a.getLitros()));
        c.put("valorTotal", texto(a.getValorTotal()));
        return c;
    }

    static Map<String, String> campos(Despesa d) {
        Map<String, String> c = new LinkedHashMap<>();
        if (d.getTipo() == TipoDespesa.EXTRA) {
            c.put("descricao", d.getDescricao());
        }
        c.put("data", texto(d.getData()));
        c.put("valor", texto(d.getValor()));
        return c;
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof BigDecimal numero) {
            return numero.stripTrailingZeros().toPlainString();
        }
        return valor.toString();
    }

    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
