package br.com.frotamais.motorista;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.caminhao.VinculoMotorista;
import br.com.frotamais.caminhao.VinculoMotoristaRepository;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.convite.ConviteService;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

/**
 * Motoristas da empresa do dono logado: cadastro por convite, vínculos com
 * caminhões, desativação, reenvio e cancelamento de convite.
 *
 * Isolamento: o motorista é sempre buscado por id + empresaId do token + perfil
 * MOTORISTA; os caminhões, por id + empresaId. Fora disso, 404.
 */
@Service
@Transactional
public class MotoristaService {

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final CaminhaoRepository caminhoes;
    private final VinculoMotoristaRepository vinculos;
    private final ConviteService convites;
    private final UsuarioLogado logado;

    public MotoristaService(UsuarioRepository usuarios, EmpresaRepository empresas, CaminhaoRepository caminhoes,
            VinculoMotoristaRepository vinculos, ConviteService convites, UsuarioLogado logado) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.caminhoes = caminhoes;
        this.vinculos = vinculos;
        this.convites = convites;
        this.logado = logado;
    }

    @Transactional(readOnly = true)
    public List<MotoristaResponse> listar() {
        Long empresaId = logado.empresaId();
        return usuarios.findAllByEmpresaIdAndPerfilOrderByNomeAscIdAsc(empresaId, Perfil.MOTORISTA).stream()
                .map(this::resposta)
                .toList();
    }

    @Transactional(readOnly = true)
    public MotoristaResponse buscar(Long id) {
        return resposta(motorista(id));
    }

    /**
     * Cria o motorista sem senha (convite pendente), vincula os caminhões e
     * envia o convite. Se o envio falhar, nada fica gravado.
     */
    public MotoristaResponse criar(MotoristaRequest req) {
        convites.exigirConfiguracao();
        Long empresaId = logado.empresaId();
        String email = Usuario.normalizarEmail(req.email());
        if (usuarios.existsByEmail(email)) {
            throw RegraNegocioException.conflito(
                    "E-mail já cadastrado. Quem trabalha em outra empresa precisa usar outro e-mail.");
        }
        List<Caminhao> lista = caminhoesParaVincular(empresaId, req.caminhaoIdsOuVazio());

        Usuario motorista = new Usuario();
        motorista.setNome(req.nome().trim());
        motorista.setEmail(email);
        motorista.setPerfil(Perfil.MOTORISTA);
        motorista.setAtivo(false);
        motorista.setEmpresa(empresas.getReferenceById(empresaId));
        usuarios.save(motorista);

        Usuario autor = usuarios.getReferenceById(logado.usuarioId());
        lista.forEach(c -> vinculos.save(new VinculoMotorista(motorista, c, autor)));
        Instant expira = convites.enviar(motorista, autor);
        return MotoristaResponse.de(motorista, idsDe(lista), expira);
    }

    public MotoristaResponse atualizar(Long id, MotoristaAtualizacaoRequest req) {
        Usuario motorista = motorista(id);
        if (motorista.isAtivacaoPendente() && req.ativo()) {
            throw RegraNegocioException.conflito(
                    "O motorista ainda não aceitou o convite: a conta é ativada por ele, ao criar a senha.");
        }
        motorista.setNome(req.nome().trim());
        if (!motorista.isAtivacaoPendente()) {
            motorista.setAtivo(req.ativo());
        }
        return resposta(motorista);
    }

    /** Substitui a lista de caminhões do motorista. */
    public MotoristaResponse definirCaminhoes(Long id, VinculosRequest req) {
        Long empresaId = logado.empresaId();
        Usuario motorista = motorista(id);
        List<Caminhao> lista = caminhoesParaVincular(empresaId, req.caminhaoIds());
        vinculos.removerTodosDoMotorista(empresaId, motorista.getId());
        Usuario autor = usuarios.getReferenceById(logado.usuarioId());
        lista.forEach(c -> vinculos.save(new VinculoMotorista(motorista, c, autor)));
        vinculos.flush();
        return resposta(motorista);
    }

    /** Novo convite para quem ainda não ativou a conta; os anteriores deixam de valer. */
    public MotoristaResponse reenviarConvite(Long id) {
        Usuario motorista = motorista(id);
        convites.enviar(motorista, usuarios.getReferenceById(logado.usuarioId()));
        return resposta(motorista);
    }

    /** Cancela o convite pendente; a conta continua aguardando ativação (situação CONVITE_PENDENTE, sem expiração). */
    public MotoristaResponse cancelarConvite(Long id) {
        Usuario motorista = motorista(id);
        convites.cancelar(motorista);
        return resposta(motorista);
    }

    private Usuario motorista(Long id) {
        return usuarios.findByIdAndEmpresaId(id, logado.empresaId())
                .filter(u -> u.getPerfil() == Perfil.MOTORISTA)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Motorista não encontrado."));
    }

    /** Todos os caminhões precisam ser da empresa e não excluídos. Ids repetidos contam uma vez. */
    private List<Caminhao> caminhoesParaVincular(Long empresaId, List<Long> ids) {
        Set<Long> unicos = new LinkedHashSet<>(ids);
        if (unicos.isEmpty()) {
            return List.of();
        }
        List<Caminhao> encontrados = caminhoes.findAllByEmpresaIdAndIdIn(empresaId, unicos);
        if (encontrados.size() != unicos.size()) {
            throw RegraNegocioException.naoEncontrado("Caminhão não encontrado.");
        }
        if (encontrados.stream().anyMatch(Caminhao::isExcluido)) {
            throw RegraNegocioException.conflito("Caminhão excluído não pode ser vinculado a motorista.");
        }
        return encontrados;
    }

    private MotoristaResponse resposta(Usuario motorista) {
        Instant expira = motorista.isAtivacaoPendente() ? convites.expiracaoPendente(motorista) : null;
        return MotoristaResponse.de(motorista, vinculos.idsDosCaminhoes(logado.empresaId(), motorista.getId()), expira);
    }

    private static List<Long> idsDe(List<Caminhao> lista) {
        return lista.stream().map(Caminhao::getId).sorted().toList();
    }
}
