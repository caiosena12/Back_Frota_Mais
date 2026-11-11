package br.com.frotamais.viagem;

import java.time.LocalDate;

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
    private final PoliticaCalculo politica;

    public LancamentoService(ViagemService viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, UsuarioRepository usuarios, UsuarioLogado logado,
            PoliticaCalculo politica) {
        this.politica = politica;
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
        if (v.isFinalizada()) {
            // Correção explícita de um frete finalizado: regrava a fotografia só dele. A
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
        reconsolidarSeFinalizada(v);
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
        reconsolidarSeFinalizada(v);
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
        Viagem v = viagens.travarAcessivel(viagemId, logado.empresaId());
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito(
                    "Viagem finalizada: não aceita novos lançamentos. Corrija um lançamento existente.");
        }
        validarData(v, data);
        return v;
    }

    /** Localiza a viagem da empresa e confere se a data do lançamento cabe no período dela. */
    private Viagem viagemParaLancar(Long viagemId, LocalDate data) {
        Viagem v = viagens.travarAcessivel(viagemId, logado.empresaId());
        viagens.exigirAlteravelPeloPerfil(v);
        validarData(v, data);
        return v;
    }

    private static void validarData(Viagem v, LocalDate data) {
        RegrasLancamento.validarData(v.getDataInicio(), v.getDataFim(), data);
        v.marcarAlteracao();
    }

    /** Ver RegrasLancamento.validarAbastecimento. */
    private void validarAbastecimento(Viagem v, AbastecimentoRequest req) {
        RegrasLancamento.validarAbastecimento(v.getCaminhao(), req, politica.limites());
    }

    /** Correção de pedágio ou despesa de frete finalizado: regrava a fotografia só dele, com o mesmo preço. */
    private void reconsolidarSeFinalizada(Viagem v) {
        if (v.isFinalizada()) {
            viagens.consolidar(logado.empresaId(), v, false);
        }
    }

    private Viagem viagemParaExcluir(Long viagemId) {
        Viagem v = viagens.travarAcessivel(viagemId, logado.empresaId());
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

    private static void preencher(Despesa d, DespesaExtraRequest req) {
        d.setDescricao(req.descricao().trim());
        d.setData(req.data());
        d.setValor(req.valor());
    }
}
