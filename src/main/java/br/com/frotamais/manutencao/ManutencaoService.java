package br.com.frotamais.manutencao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.UsuarioRepository;

/**
 * Manutenções da empresa do usuário logado (o controller restringe ao DONO).
 *
 * O caminhão precisa ser da empresa (senão 404) e não estar excluído: o
 * histórico de um caminhão excluído fica congelado, então suas manutenções
 * também não são incluídas, alteradas nem excluídas. Caminhão inativo aceita
 * (pode estar parado justamente em manutenção).
 *
 * A exclusão é lógica. Manutenção não altera viagens nem a fotografia
 * financeira delas; entra só nos totais do período (IndicadorService).
 */
@Service
@Transactional
public class ManutencaoService {

    private final ManutencaoRepository repository;
    private final CaminhaoRepository caminhoes;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;

    public ManutencaoService(ManutencaoRepository repository, CaminhaoRepository caminhoes,
            EmpresaRepository empresas, UsuarioRepository usuarios, UsuarioLogado logado) {
        this.repository = repository;
        this.caminhoes = caminhoes;
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.logado = logado;
    }

    @Transactional(readOnly = true)
    public List<ManutencaoResponse> listar(Long caminhaoId, LocalDate de, LocalDate ate, boolean incluirExcluidas) {
        if (de != null && ate != null && ate.isBefore(de)) {
            throw RegraNegocioException.invalido("A data final do filtro não pode ser anterior à inicial.");
        }
        return repository.listar(logado.empresaId(), caminhaoId, de, ate, incluirExcluidas).stream()
                .map(ManutencaoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ManutencaoResponse buscar(Long id) {
        return ManutencaoResponse.de(repository.buscar(id, logado.empresaId()).orElseThrow(this::naoEncontrada));
    }

    public ManutencaoResponse criar(ManutencaoRequest req) {
        Long empresaId = logado.empresaId();
        Caminhao caminhao = caminhaoOperacional(req.caminhaoId(), empresaId);
        exigirDataValida(req.data());
        Manutencao m = new Manutencao();
        m.setEmpresa(empresas.getReferenceById(empresaId));
        m.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        preencher(m, caminhao, req);
        return ManutencaoResponse.de(repository.save(m));
    }

    public ManutencaoResponse atualizar(Long id, ManutencaoRequest req) {
        Long empresaId = logado.empresaId();
        Manutencao m = alteravel(id, empresaId);
        Caminhao caminhao = caminhaoOperacional(req.caminhaoId(), empresaId);
        exigirDataValida(req.data());
        preencher(m, caminhao, req);
        m.marcarAlteracao();
        return ManutencaoResponse.de(m);
    }

    public void excluir(Long id) {
        Manutencao m = alteravel(id, logado.empresaId());
        m.excluir(usuarios.getReferenceById(logado.usuarioId()));
        m.marcarAlteracao();
    }

    /** Manutenção da empresa, não excluída, de caminhão não excluído. */
    private Manutencao alteravel(Long id, Long empresaId) {
        Manutencao m = repository.travar(id, empresaId).orElseThrow(this::naoEncontrada);
        if (m.isExcluida()) {
            throw RegraNegocioException.conflito("A manutenção já foi excluída.");
        }
        if (m.getCaminhao().isExcluido()) {
            throw RegraNegocioException.conflito("Manutenção de caminhão excluído não pode ser alterada.");
        }
        return m;
    }

    private Caminhao caminhaoOperacional(Long caminhaoId, Long empresaId) {
        // Bloqueia o caminhão (como a exclusão dele), para uma exclusão simultânea não passar despercebida.
        Caminhao c = caminhoes.travarPorIdEEmpresa(caminhaoId, empresaId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Caminhão não encontrado."));
        if (c.isExcluido()) {
            throw RegraNegocioException.conflito("Caminhão excluído não recebe manutenções.");
        }
        return c;
    }

    private static void exigirDataValida(LocalDate data) {
        if (data.isAfter(Datas.hoje())) {
            throw RegraNegocioException.invalido("A data da manutenção não pode estar no futuro.");
        }
    }

    private static void preencher(Manutencao m, Caminhao caminhao, ManutencaoRequest req) {
        m.setCaminhao(caminhao);
        m.setData(req.data());
        m.setDescricao(req.descricao().trim());
        m.setValor(req.valor());
    }

    private RegraNegocioException naoEncontrada() {
        return RegraNegocioException.naoEncontrado("Manutenção não encontrada.");
    }
}
