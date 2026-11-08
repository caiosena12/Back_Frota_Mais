package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.UsuarioRepository;

/**
 * Abastecimentos, pedágios e despesas extras de um frete.
 *
 * Cada operação primeiro localiza (e bloqueia) a viagem pelo empresaId do
 * token; depois busca o lançamento pelo id da viagem e da empresa. Assim um
 * id de lançamento de outra viagem ou de outra empresa responde 404.
 *
 * Inclusão e exclusão só com a viagem em andamento. Depois de finalizada, o
 * dono pode apenas corrigir (PUT) os lançamentos existentes, sem tirá-los do
 * período da viagem; nada é incluído nem apagado, para preservar o histórico.
 */
@Service
@Transactional
public class LancamentoService {

    private final ViagemService viagens;
    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;
    private final LimitesPlausibilidade limites;

    public LancamentoService(ViagemService viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, UsuarioRepository usuarios, UsuarioLogado logado,
            LimitesPlausibilidade limites) {
        this.limites = limites;
        this.viagens = viagens;
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
        this.usuarios = usuarios;
        this.logado = logado;
    }

    // ---- Abastecimentos (diesel e Arla) ----

    public AbastecimentoResponse adicionarAbastecimento(Long viagemId, AbastecimentoRequest req) {
        Viagem v = viagemParaIncluir(viagemId, req.data());
        validarAbastecimento(v, req);
        Abastecimento a = new Abastecimento();
        a.setViagem(v);
        a.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        preencher(a, req);
        return AbastecimentoResponse.de(abastecimentos.save(a));
    }

    public AbastecimentoResponse atualizarAbastecimento(Long viagemId, Long id, AbastecimentoRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
        validarAbastecimento(v, req);
        Abastecimento a = abastecimento(v, id);
        boolean envolveDiesel = a.getTipo() == TipoCombustivel.DIESEL || req.tipo() == TipoCombustivel.DIESEL;
        preencher(a, req);
        if (v.isFinalizada() && envolveDiesel) {
            // Correção do diesel de um frete finalizado: recalcula a referência só dele.
            viagens.consolidarReferencia(logado.empresaId(), v);
        }
        return AbastecimentoResponse.de(a);
    }

    public void excluirAbastecimento(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        abastecimentos.delete(abastecimento(v, id));
    }

    // ---- Pedágios ----

    public PedagioResponse adicionarPedagio(Long viagemId, PedagioRequest req) {
        Viagem v = viagemParaIncluir(viagemId, req.data());
        Despesa d = novaDespesa(v, TipoDespesa.PEDAGIO);
        d.setData(req.data());
        d.setValor(req.valor());
        return PedagioResponse.de(despesas.save(d));
    }

    public PedagioResponse atualizarPedagio(Long viagemId, Long id, PedagioRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
        Despesa d = despesa(v, id, TipoDespesa.PEDAGIO);
        d.setData(req.data());
        d.setValor(req.valor());
        return PedagioResponse.de(d);
    }

    public void excluirPedagio(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        despesas.delete(despesa(v, id, TipoDespesa.PEDAGIO));
    }

    // ---- Despesas extras ----

    public DespesaExtraResponse adicionarDespesaExtra(Long viagemId, DespesaExtraRequest req) {
        Viagem v = viagemParaIncluir(viagemId, req.data());
        Despesa d = novaDespesa(v, TipoDespesa.EXTRA);
        preencher(d, req);
        return DespesaExtraResponse.de(despesas.save(d));
    }

    public DespesaExtraResponse atualizarDespesaExtra(Long viagemId, Long id, DespesaExtraRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
        Despesa d = despesa(v, id, TipoDespesa.EXTRA);
        preencher(d, req);
        return DespesaExtraResponse.de(d);
    }

    public void excluirDespesaExtra(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        despesas.delete(despesa(v, id, TipoDespesa.EXTRA));
    }

    // ---- Regras comuns ----

    /**
     * Inclusão de lançamento novo: só com a viagem em andamento. Depois da
     * finalização o dono apenas corrige (PUT) os lançamentos existentes.
     */
    private Viagem viagemParaIncluir(Long viagemId, LocalDate data) {
        Viagem v = viagens.travar(viagemId, logado.empresaId());
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito(
                    "Viagem finalizada: não aceita novos lançamentos. Corrija um lançamento existente.");
        }
        validarData(v, data);
        return v;
    }

    /** Localiza a viagem da empresa e confere se a data do lançamento cabe no período dela. */
    private Viagem viagemParaLancar(Long viagemId, LocalDate data) {
        Viagem v = viagens.travar(viagemId, logado.empresaId());
        validarData(v, data);
        return v;
    }

    private static void validarData(Viagem v, LocalDate data) {
        if (data.isAfter(ViagemService.hoje())) {
            throw RegraNegocioException.invalido("A data do lançamento não pode estar no futuro.");
        }
        if (data.isBefore(v.getDataInicio())) {
            throw RegraNegocioException.invalido(
                    "A data do lançamento não pode ser anterior ao início da viagem.");
        }
        if (v.getDataFim() != null && data.isAfter(v.getDataFim())) {
            throw RegraNegocioException.invalido(
                    "A data do lançamento não pode ser posterior ao fim da viagem.");
        }
        v.marcarAlteracao();
    }

    /**
     * Diesel: preço por litro dentro dos limites de plausibilidade e, se o
     * caminhão tiver capacidade do tanque cadastrada, litros até essa capacidade.
     * Sem capacidade cadastrada não há limite de litros. Arla tem tanque próprio
     * e não é limitado pela capacidade do tanque de diesel.
     */
    private void validarAbastecimento(Viagem v, AbastecimentoRequest req) {
        if (req.tipo() != TipoCombustivel.DIESEL) {
            return;
        }
        BigDecimal precoLitro = req.valorTotal().divide(req.litros(), MathContext.DECIMAL128);
        if (!limites.precoValido(precoLitro)) {
            throw RegraNegocioException.invalido("Preço do diesel fora do limite de plausibilidade (R$ "
                    + LimitesPlausibilidade.br(precoLitro) + " por litro). Informe de " + limites.faixaPreco()
                    + "; confira se litros e valor não foram trocados.");
        }
        Integer capacidade = v.getCaminhao().getCapacidadeTanqueLitros();
        if (capacidade != null && req.litros().compareTo(BigDecimal.valueOf(capacidade)) > 0) {
            throw RegraNegocioException.invalido("Os litros abastecidos ultrapassam a capacidade do tanque do"
                    + " caminhão (" + capacidade + " L).");
        }
    }

    private Viagem viagemParaExcluir(Long viagemId) {
        Viagem v = viagens.travar(viagemId, logado.empresaId());
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito(
                    "Viagem finalizada: lançamentos podem ser corrigidos, mas não excluídos.");
        }
        v.marcarAlteracao();
        return v;
    }

    private Abastecimento abastecimento(Viagem v, Long id) {
        return abastecimentos.findByIdAndViagemIdAndViagemEmpresaId(id, v.getId(), logado.empresaId())
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Abastecimento não encontrado."));
    }

    private Despesa despesa(Viagem v, Long id, TipoDespesa tipo) {
        return despesas.findByIdAndViagemIdAndViagemEmpresaIdAndTipo(id, v.getId(), logado.empresaId(), tipo)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado(
                        tipo == TipoDespesa.PEDAGIO ? "Pedágio não encontrado." : "Despesa não encontrada."));
    }

    private Despesa novaDespesa(Viagem v, TipoDespesa tipo) {
        Despesa d = new Despesa();
        d.setViagem(v);
        d.setTipo(tipo);
        d.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        return d;
    }

    private static void preencher(Abastecimento a, AbastecimentoRequest req) {
        a.setTipo(req.tipo());
        a.setData(req.data());
        a.setLitros(req.litros());
        a.setValorTotal(req.valorTotal());
    }

    /** Termo proibido em despesa extra, já normalizado (sem acento, minúsculo). */
    private static final String ALIMENTACAO = "alimentacao";

    /**
     * Alimentação não é custo do Frota+. Compara a descrição sem acentos, sem
     * diferença de maiúsculas e com espaços normalizados, e recusa qualquer
     * descrição que contenha o termo.
     */
    static boolean ehAlimentacao(String descricao) {
        String normalizada = Normalizer.normalize(descricao, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return normalizada.contains(ALIMENTACAO);
    }

    private static void preencher(Despesa d, DespesaExtraRequest req) {
        if (ehAlimentacao(req.descricao())) {
            throw RegraNegocioException.invalido(
                    "Alimentação não é despesa do frete e não entra nos custos do Frota+.");
        }
        d.setDescricao(req.descricao().trim());
        d.setData(req.data());
        d.setValor(req.valor());
    }
}
