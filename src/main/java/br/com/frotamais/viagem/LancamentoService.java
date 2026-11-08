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
 * Exclusão só com a viagem em andamento. Depois de finalizada, o lançamento
 * pode ser corrigido (PUT), mas não apagado, para preservar o histórico.
 */
@Service
@Transactional
public class LancamentoService {

    private final ViagemService viagens;
    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;

    public LancamentoService(ViagemService viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, UsuarioRepository usuarios, UsuarioLogado logado) {
        this.viagens = viagens;
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
        this.usuarios = usuarios;
        this.logado = logado;
    }

    // ---- Abastecimentos (diesel e Arla) ----

    public AbastecimentoResponse adicionarAbastecimento(Long viagemId, AbastecimentoRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
        Abastecimento a = new Abastecimento();
        a.setViagem(v);
        a.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        preencher(a, req);
        return AbastecimentoResponse.de(abastecimentos.save(a));
    }

    public AbastecimentoResponse atualizarAbastecimento(Long viagemId, Long id, AbastecimentoRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
        Abastecimento a = abastecimento(v, id);
        preencher(a, req);
        return AbastecimentoResponse.de(a);
    }

    public void excluirAbastecimento(Long viagemId, Long id) {
        Viagem v = viagemParaExcluir(viagemId);
        abastecimentos.delete(abastecimento(v, id));
    }

    // ---- Pedágios ----

    public PedagioResponse adicionarPedagio(Long viagemId, PedagioRequest req) {
        Viagem v = viagemParaLancar(viagemId, req.data());
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
        Viagem v = viagemParaLancar(viagemId, req.data());
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

    /** Localiza a viagem da empresa e confere se a data do lançamento cabe no período dela. */
    private Viagem viagemParaLancar(Long viagemId, LocalDate data) {
        Viagem v = viagens.travar(viagemId, logado.empresaId());
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
        return v;
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

    private static void preencher(Despesa d, DespesaExtraRequest req) {
        d.setDescricao(req.descricao().trim());
        d.setData(req.data());
        d.setValor(req.valor());
    }
}
