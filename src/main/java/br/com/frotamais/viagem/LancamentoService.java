package br.com.frotamais.viagem;

import java.time.LocalDate;
import java.util.Map;

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
 * Inclusão e exclusão só com a viagem em andamento. Depois de encerrada
 * (finalizada ou cancelada), o dono pode apenas corrigir (PUT) os lançamentos
 * existentes, sem tirá-los do período da viagem; nada é incluído nem apagado,
 * e cada campo corrigido fica registrado na auditoria (AuditoriaViagem).
 */
@Service
@Transactional
public class LancamentoService {

    private final ViagemService viagens;
    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;
    private final PoliticaCalculo politica;
    private final AuditoriaViagem auditoria;

    public LancamentoService(ViagemService viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, UsuarioRepository usuarios, UsuarioLogado logado,
            PoliticaCalculo politica, AuditoriaViagem auditoria) {
        this.auditoria = auditoria;
        this.politica = politica;
        this.viagens = viagens;
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
        this.usuarios = usuarios;
        this.logado = logado;
    }

    // ---- Abastecimentos (diesel e Arla) ----

    public AbastecimentoResponse adicionarAbastecimento(Long viagemId, AbastecimentoRequest req) {
        Viagem v = viagemParaIncluir(viagemId, null, true);
        viagens.validarDataAbastecimento(v, logado.empresaId(), req.data(), req.hora());
        validarAbastecimento(v, req);
        Abastecimento a = new Abastecimento();
        a.setViagem(v);
        a.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        preencher(a, req);
        abastecimentos.save(a);
        registrarInclusaoSeCancelada(v, AlteracaoViagem.Entidade.ABASTECIMENTO, a.getId(),
                AuditoriaViagem.campos(a), a.getTipo() == TipoCombustivel.DIESEL);
        return AbastecimentoResponse.de(a);
    }

    public AbastecimentoResponse atualizarAbastecimento(Long viagemId, Long id, AbastecimentoRequest req) {
        Viagem v = viagemParaLancar(viagemId, null, true);
        viagens.validarDataAbastecimento(v, logado.empresaId(), req.data(), req.hora());
        validarAbastecimento(v, req);
        Abastecimento a = abastecimento(v, id);
        boolean envolveDiesel = a.getTipo() == TipoCombustivel.DIESEL || req.tipo() == TipoCombustivel.DIESEL;
        Map<String, String> antes = AuditoriaViagem.campos(a);
        preencher(a, req);
        if (v.isEncerrada()) {
            auditoria.registrar(v, AlteracaoViagem.Entidade.ABASTECIMENTO, a.getId(), antes, AuditoriaViagem.campos(a));
            // Correção explícita de um frete encerrado: regrava a fotografia só dele. A
            // referência de preço só é reavaliada se a correção envolve diesel.
            viagens.consolidar(logado.empresaId(), v, envolveDiesel);
        }
        return AbastecimentoResponse.de(a);
    }

    public void excluirAbastecimento(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        abastecimentos.delete(abastecimento(v, id));
    }

    // ---- Pedágios ----

    public PedagioResponse adicionarPedagio(Long viagemId, PedagioRequest req) {
        Viagem v = viagemParaIncluir(viagemId, req.data(), false);
        Despesa d = novaDespesa(v, TipoDespesa.PEDAGIO);
        d.setData(req.data());
        d.setValor(req.valor());
        despesas.save(d);
        registrarInclusaoSeCancelada(v, AlteracaoViagem.Entidade.PEDAGIO, d.getId(), AuditoriaViagem.campos(d), false);
        return PedagioResponse.de(d);
    }

    public PedagioResponse atualizarPedagio(Long viagemId, Long id, PedagioRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data(), false);
        Despesa d = despesa(v, id, TipoDespesa.PEDAGIO);
        Map<String, String> antes = AuditoriaViagem.campos(d);
        d.setData(req.data());
        d.setValor(req.valor());
        reconsolidarSeEncerrada(v, AlteracaoViagem.Entidade.PEDAGIO, d, antes);
        return PedagioResponse.de(d);
    }

    public void excluirPedagio(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        despesas.delete(despesa(v, id, TipoDespesa.PEDAGIO));
    }

    // ---- Despesas extras ----

    public DespesaExtraResponse adicionarDespesaExtra(Long viagemId, DespesaExtraRequest req) {
        Viagem v = viagemParaIncluir(viagemId, req.data(), false);
        Despesa d = novaDespesa(v, TipoDespesa.EXTRA);
        preencher(d, req);
        despesas.save(d);
        registrarInclusaoSeCancelada(v, AlteracaoViagem.Entidade.DESPESA_EXTRA, d.getId(),
                AuditoriaViagem.campos(d), false);
        return DespesaExtraResponse.de(d);
    }

    public DespesaExtraResponse atualizarDespesaExtra(Long viagemId, Long id, DespesaExtraRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data(), false);
        Despesa d = despesa(v, id, TipoDespesa.EXTRA);
        Map<String, String> antes = AuditoriaViagem.campos(d);
        preencher(d, req);
        reconsolidarSeEncerrada(v, AlteracaoViagem.Entidade.DESPESA_EXTRA, d, antes);
        return DespesaExtraResponse.de(d);
    }

    public void excluirDespesaExtra(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        despesas.delete(despesa(v, id, TipoDespesa.EXTRA));
    }

    // ---- Regras comuns ----

    /**
     * Inclusão de lançamento novo:
     * - viagem em andamento: DONO ou MOTORISTA com acesso;
     * - viagem cancelada: só o DONO (valores reais da operação cancelada, como
     *   despesas que surgiram depois), dentro do período; fica na auditoria;
     * - viagem finalizada: ninguém; o dono corrige (PUT) os lançamentos existentes.
     */
    private Viagem viagemParaIncluir(Long viagemId, LocalDate data, boolean abastecimento) {
        Viagem v = travar(viagemId, abastecimento);
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito(
                    "Viagem finalizada: não aceita novos lançamentos. Corrija um lançamento existente.");
        }
        viagens.exigirAlteravelPeloPerfil(v);
        validarData(v, data);
        return v;
    }

    /**
     * Lançamento incluído em viagem cancelada: entra na auditoria (sem valor
     * anterior) e a fotografia da viagem é regravada, com a anterior arquivada.
     */
    private void registrarInclusaoSeCancelada(Viagem v, AlteracaoViagem.Entidade entidade, Long id,
            Map<String, String> campos, boolean envolveDiesel) {
        if (v.isCancelada()) {
            auditoria.registrar(v, entidade, id, Map.of(), campos);
            viagens.consolidar(logado.empresaId(), v, envolveDiesel);
        }
    }

    /** Localiza a viagem da empresa e confere se a data do lançamento cabe no período dela. */
    private Viagem viagemParaLancar(Long viagemId, LocalDate data, boolean abastecimento) {
        Viagem v = travar(viagemId, abastecimento);
        viagens.exigirAlteravelPeloPerfil(v);
        validarData(v, data);
        return v;
    }

    /**
     * Abastecimento: bloqueia o caminhão antes da viagem (mesma ordem da
     * abertura e das correções de datas). Ele pode ter data anterior ao início,
     * validada contra o fim da viagem anterior do caminhão; sem esse bloqueio,
     * uma correção simultânea da viagem anterior poderia passar a data final
     * dela para depois deste abastecimento sem enxergá-lo. Pedágios e despesas
     * ficam dentro do período da própria viagem e bloqueiam só ela.
     */
    private Viagem travar(Long viagemId, boolean abastecimento) {
        return abastecimento
                ? viagens.travarComCaminhao(viagemId, logado.empresaId())
                : viagens.travarAcessivel(viagemId, logado.empresaId());
    }

    /** data nula: a data é validada pelo chamador (abastecimento, que tem regra própria). */
    private static void validarData(Viagem v, LocalDate data) {
        if (data != null) {
            RegrasLancamento.validarData(v.getDataInicio(), v.getDataFim(), data);
        }
        v.marcarAlteracao();
    }

    /** Ver RegrasLancamento.validarAbastecimento. */
    private void validarAbastecimento(Viagem v, AbastecimentoRequest req) {
        RegrasLancamento.validarAbastecimento(v.getCaminhao(), req, politica.limites());
    }

    /** Correção de pedágio ou despesa de frete encerrado: regrava a fotografia só dele, com o mesmo preço. */
    private void reconsolidarSeEncerrada(Viagem v, AlteracaoViagem.Entidade entidade, Despesa d,
            Map<String, String> antes) {
        if (v.isEncerrada()) {
            auditoria.registrar(v, entidade, d.getId(), antes, AuditoriaViagem.campos(d));
            viagens.consolidar(logado.empresaId(), v, false);
        }
    }

    private Viagem viagemParaExcluir(Long viagemId) {
        Viagem v = viagens.travarAcessivel(viagemId, logado.empresaId());
        if (v.isEncerrada()) {
            throw RegraNegocioException.conflito("Viagem " + encerrada(v)
                    + ": lançamentos podem ser corrigidos, mas não excluídos.");
        }
        v.marcarAlteracao();
        return v;
    }

    private static String encerrada(Viagem v) {
        return v.isCancelada() ? "cancelada" : "finalizada";
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
        a.setHora(req.hora());
        a.setLitros(req.litros());
        a.setValorTotal(req.valorTotal());
    }

    private static void preencher(Despesa d, DespesaExtraRequest req) {
        d.setDescricao(req.descricao().trim());
        d.setData(req.data());
        d.setValor(req.valor());
    }
}
