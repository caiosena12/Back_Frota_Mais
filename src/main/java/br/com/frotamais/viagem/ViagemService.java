package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.AcessoCaminhao;
import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.caminhao.VinculoMotoristaRepository;
import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.Perfil;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Ciclo de vida do frete: abertura, alteração, finalização e consulta.
 *
 * Isolamento: o empresaId vem sempre de UsuarioLogado. Viagem de outra
 * empresa, ou inexistente, responde 404.
 */
@Service
@Transactional
public class ViagemService {

    static final ZoneId FUSO = Datas.FUSO;

    private final ViagemRepository viagens;
    private final AbastecimentoRepository abastecimentos;
    private final DespesaRepository despesas;
    private final CaminhaoRepository caminhoes;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final UsuarioLogado logado;
    private final RentabilidadeService rentabilidade;
    private final PoliticaCalculo politica;
    private final AcessoCaminhao acesso;
    private final VinculoMotoristaRepository vinculos;
    private final AuditoriaViagem auditoria;
    private final AlteracaoViagemRepository alteracoes;
    private final FotografiaAnteriorRepository fotografias;
    private final ObjectMapper json;

    public ViagemService(ViagemRepository viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, CaminhaoRepository caminhoes, EmpresaRepository empresas,
            UsuarioRepository usuarios, UsuarioLogado logado, RentabilidadeService rentabilidade,
            PoliticaCalculo politica, AcessoCaminhao acesso, VinculoMotoristaRepository vinculos,
            AuditoriaViagem auditoria, AlteracaoViagemRepository alteracoes,
            FotografiaAnteriorRepository fotografias, ObjectMapper json) {
        this.auditoria = auditoria;
        this.alteracoes = alteracoes;
        this.fotografias = fotografias;
        this.json = json;
        this.acesso = acesso;
        this.vinculos = vinculos;
        this.politica = politica;
        this.viagens = viagens;
        this.abastecimentos = abastecimentos;
        this.despesas = despesas;
        this.caminhoes = caminhoes;
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.logado = logado;
        this.rentabilidade = rentabilidade;
    }

    @Transactional(readOnly = true)
    public List<ViagemResumoResponse> listar(StatusViagem status, Long caminhaoId) {
        Long empresaId = logado.empresaId();
        if (logado.isMotorista()) {
            // Só caminhões vinculados, e sem custo, lucro e margem.
            return viagens.listarDoMotorista(empresaId, logado.usuarioId(), status, caminhaoId).stream()
                    .map(v -> ViagemResumoResponse.de(v, null)).toList();
        }
        List<Viagem> lista = viagens.listar(empresaId, status, caminhaoId);
        Map<Long, RentabilidadeResponse> calculos = rentabilidade.calcular(empresaId, lista);
        return lista.stream().map(v -> ViagemResumoResponse.de(v, calculos.get(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ViagemResponse buscar(Long id) {
        Long empresaId = logado.empresaId();
        Viagem v = viagens.findByIdAndEmpresaId(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
        exigirAcesso(v);
        return detalhar(empresaId, v);
    }

    /** Campos corrigidos nas viagens encerradas, em ordem cronológica. Só DONO (controller). */
    @Transactional(readOnly = true)
    public List<AlteracaoViagemResponse> alteracoes(Long id) {
        Long empresaId = logado.empresaId();
        viagens.findByIdAndEmpresaId(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
        return alteracoes.daViagem(id, empresaId).stream().map(AlteracaoViagemResponse::de).toList();
    }

    /** Versões anteriores da fotografia financeira, em ordem cronológica. Só DONO (controller). */
    @Transactional(readOnly = true)
    public List<FotografiaAnteriorResponse> fotografiasAnteriores(Long id) {
        Long empresaId = logado.empresaId();
        viagens.findByIdAndEmpresaId(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
        return fotografias.daViagem(id, empresaId).stream()
                .map(f -> new FotografiaAnteriorResponse(f.getId(), f.getSubstituidaEm(),
                        f.getSubstituidaPor().getId(), f.getSubstituidaPor().getNome(),
                        json.readTree(f.getResultado())))
                .toList();
    }

    public ViagemResponse criar(ViagemCriacaoRequest req) {
        Long empresaId = logado.empresaId();

        Caminhao caminhao = travarCaminhaoParaAbertura(req.caminhaoId(), empresaId);
        if (!acesso.podeVer(caminhao)) {
            throw AcessoCaminhao.naoEncontrado();
        }
        Usuario motorista = resolverMotorista(empresaId, caminhao, req.motoristaId(), true);
        if (viagens.existsByEmpresaIdAndCaminhaoIdAndStatus(empresaId, caminhao.getId(), StatusViagem.EM_ANDAMENTO)) {
            throw RegraNegocioException.conflito(
                    "Este caminhão já tem uma viagem em andamento. Finalize-a antes de abrir outra.");
        }

        Estado estado = new Estado(req.dataInicio(), null, req.valorFrete(), texto(req.origem()),
                texto(req.destino()), req.kmInicialVazio(), req.kmInicialCarregado(), null,
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        exigirSemSobreposicao(empresaId, caminhao.getId(), estado, null);
        // Abastecimentos iniciais: tudo validado antes de gravar a viagem, para
        // um item inválido não deixar nada criado pela metade.
        LocalDate fimAnterior = fimDaAnterior(empresaId, caminhao.getId(), estado.dataInicio(), null);
        for (AbastecimentoRequest a : req.abastecimentosOuVazio()) {
            RegrasLancamento.validarDataAbastecimento(estado.dataInicio(), null, fimAnterior, a.data(), a.hora());
            RegrasLancamento.validarAbastecimento(caminhao, a, politica.limites());
        }

        Viagem v = nova(empresaId, caminhao);
        estado.aplicar(v);
        v.setMotorista(motorista);
        viagens.save(v);
        for (AbastecimentoRequest item : req.abastecimentosOuVazio()) {
            abastecimentos.save(novoAbastecimento(v, v.getCriadoPor(), item));
        }
        return detalhar(empresaId, v);
    }

    /**
     * Cadastro retroativo de um frete esquecido, já finalizado, numa única
     * transação. Nunca nasce em andamento (um período aberto no passado
     * bloquearia todas as viagens posteriores do caminhão).
     *
     * Tudo é validado antes de gravar qualquer coisa: dados obrigatórios de
     * frete finalizado, datas, odômetro, consumo, cada lançamento e a
     * sobreposição com qualquer viagem do caminhão (inclusive em andamento).
     * A fotografia financeira é gravada só para este frete; nenhum outro frete
     * finalizado é recalculado.
     */
    public ViagemResponse criarHistorica(ViagemHistoricaRequest req) {
        Long empresaId = logado.empresaId();
        LimitesPlausibilidade limites = politica.limites();
        Caminhao caminhao = travarCaminhaoParaHistorica(req.caminhaoId(), empresaId, req.dataFim());
        Usuario motorista = resolverMotorista(empresaId, caminhao, req.motoristaId(), false);

        Estado estado = new Estado(req.dataInicio(), req.dataFim(), req.valorFrete(), texto(req.origem()),
                texto(req.destino()), req.kmInicialVazio(), req.kmInicialCarregado(), req.kmFinalCarregado(),
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(limites);
        estado.validarCompleta();
        LocalDate fimAnterior = fimDaAnterior(empresaId, caminhao.getId(), estado.dataInicio(), null);
        for (AbastecimentoRequest a : req.abastecimentosOuVazio()) {
            RegrasLancamento.validarDataAbastecimento(estado.dataInicio(), estado.dataFim(), fimAnterior,
                    a.data(), a.hora());
            RegrasLancamento.validarAbastecimento(caminhao, a, limites);
        }
        for (PedagioRequest p : req.pedagiosOuVazio()) {
            RegrasLancamento.validarData(estado.dataInicio(), estado.dataFim(), p.data());
        }
        for (DespesaExtraRequest d : req.despesasExtrasOuVazio()) {
            RegrasLancamento.validarData(estado.dataInicio(), estado.dataFim(), d.data());
        }
        exigirSemSobreposicao(empresaId, caminhao.getId(), estado, null);
        exigirSeguinteCompativel(empresaId, caminhao.getId(), estado, null);

        Instant agora = agora();
        Viagem v = nova(empresaId, caminhao);
        estado.aplicar(v);
        v.setMotorista(motorista);
        v.setStatus(StatusViagem.FINALIZADA);
        v.setFinalizadaEm(agora);
        v.setCadastroRetroativoEm(agora);
        viagens.save(v);

        Usuario autor = v.getCriadoPor();
        for (AbastecimentoRequest item : req.abastecimentosOuVazio()) {
            abastecimentos.save(novoAbastecimento(v, autor, item));
        }
        for (PedagioRequest p : req.pedagiosOuVazio()) {
            despesas.save(despesa(v, autor, TipoDespesa.PEDAGIO, null, p.data(), p.valor()));
        }
        for (DespesaExtraRequest d : req.despesasExtrasOuVazio()) {
            despesas.save(despesa(v, autor, TipoDespesa.EXTRA, d.descricao().trim(),
                    d.data(), d.valor()));
        }

        consolidar(empresaId, v, true);
        return detalhar(empresaId, v);
    }

    private static Abastecimento novoAbastecimento(Viagem v, Usuario autor, AbastecimentoRequest item) {
        Abastecimento a = new Abastecimento();
        a.setViagem(v);
        a.setCriadoPor(autor);
        a.setTipo(item.tipo());
        a.setData(item.data());
        a.setHora(item.hora());
        a.setLitros(item.litros());
        a.setValorTotal(item.valorTotal());
        return a;
    }

    private Viagem nova(Long empresaId, Caminhao caminhao) {
        Viagem v = new Viagem();
        v.setEmpresa(empresas.getReferenceById(empresaId));
        v.setCaminhao(caminhao);
        v.setCriadoPor(usuarios.getReferenceById(logado.usuarioId()));
        return v;
    }

    private static Despesa despesa(Viagem v, Usuario autor, TipoDespesa tipo, String descricao,
            LocalDate data, BigDecimal valor) {
        Despesa d = new Despesa();
        d.setViagem(v);
        d.setCriadoPor(autor);
        d.setTipo(tipo);
        d.setDescricao(descricao);
        d.setData(data);
        d.setValor(valor);
        return d;
    }

    /**
     * Nova viagem: só em caminhão ATIVO. O bloqueio do caminhão serializa
     * aberturas e cadastros simultâneos no mesmo caminhão.
     */
    private Caminhao travarCaminhaoParaAbertura(Long caminhaoId, Long empresaId) {
        Caminhao caminhao = travarCaminhao(caminhaoId, empresaId);
        switch (caminhao.getStatus()) {
            case ATIVO -> { }
            case INATIVO -> throw RegraNegocioException.conflito("Caminhão inativo não pode iniciar viagem.");
            case EXCLUIDO -> throw RegraNegocioException.conflito("Caminhão excluído não pode iniciar viagem.");
        }
        return caminhao;
    }

    /**
     * Viagem histórica (já aconteceu):
     * - ATIVO: aceita;
     * - INATIVO: aceita se a viagem terminou antes da data de inativação;
     * - EXCLUIDO: recusada. Se a regra para caminhão excluído for definida,
     *   ela entra só aqui (o momento da exclusão já fica gravado em excluidoEm).
     */
    private Caminhao travarCaminhaoParaHistorica(Long caminhaoId, Long empresaId, LocalDate dataFim) {
        Caminhao caminhao = travarCaminhao(caminhaoId, empresaId);
        switch (caminhao.getStatus()) {
            case ATIVO -> { }
            case INATIVO -> {
                LocalDate inativacao = caminhao.dataInativacao(FUSO);
                if (inativacao == null) {
                    throw RegraNegocioException.conflito("A data de inativação deste caminhão não é conhecida;"
                            + " reative-o para cadastrar a viagem histórica.");
                }
                if (!dataFim.isBefore(inativacao)) {
                    throw RegraNegocioException.conflito("Caminhão inativo só recebe viagem histórica que terminou"
                            + " antes da inativação, em " + inativacao + ".");
                }
            }
            case EXCLUIDO -> throw RegraNegocioException.conflito(
                    "Caminhão excluído não recebe cadastro de viagem histórica.");
        }
        return caminhao;
    }

    private Caminhao travarCaminhao(Long caminhaoId, Long empresaId) {
        return caminhoes.travarPorIdEEmpresa(caminhaoId, empresaId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Caminhão não encontrado."));
    }

    public ViagemResponse atualizar(Long id, ViagemAtualizacaoRequest req) {
        Long empresaId = logado.empresaId();
        Viagem v = travarComCaminhao(id, empresaId);
        exigirAlteravelPeloPerfil(v);
        Usuario motorista;
        if (logado.isMotorista()) {
            if (req.motoristaId() != null && !req.motoristaId().equals(logado.usuarioId())) {
                throw RegraNegocioException.proibido("O motorista não troca o responsável pela viagem.");
            }
            motorista = v.getMotorista();
        } else {
            motorista = resolverMotorista(empresaId, v.getCaminhao(), req.motoristaId(), !v.isEncerrada());
        }

        if (!v.isEncerrada() && (req.dataFim() != null || req.kmFinalCarregado() != null)) {
            throw RegraNegocioException.invalido(
                    "Data final e quilometragem final são informadas na finalização da viagem.");
        }

        Estado estado = new Estado(req.dataInicio(),
                v.isEncerrada() ? req.dataFim() : null,
                req.valorFrete(), texto(req.origem()), texto(req.destino()),
                req.kmInicialVazio(), req.kmInicialCarregado(),
                v.isEncerrada() ? req.kmFinalCarregado() : null,
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        if (v.isFinalizada()) {
            // Correção de viagem finalizada: ela precisa continuar completa e coerente.
            estado.validarCompleta();
        } else if (v.isCancelada() && estado.dataFim() == null) {
            // Cancelada continua com o período fechado; os demais dados podem ficar incompletos.
            throw RegraNegocioException.invalido("Viagem cancelada precisa manter a data final.");
        }
        validarDatasDosLancamentos(v, empresaId, estado);
        exigirSemSobreposicao(empresaId, v.getCaminhao().getId(), estado, v.getId());
        exigirSeguinteCompativel(empresaId, v.getCaminhao().getId(), estado, v.getId());

        Map<String, String> antes = v.isEncerrada() ? AuditoriaViagem.campos(v) : null;
        estado.aplicar(v);
        v.setMotorista(motorista);
        v.marcarAlteracao();
        if (v.isEncerrada()) {
            auditoria.registrar(v, AlteracaoViagem.Entidade.VIAGEM, v.getId(), antes, AuditoriaViagem.campos(v));
            // Correção explícita de um frete encerrado: regrava a fotografia só dele,
            // com a referência de preço e a política de cálculo atuais.
            consolidar(empresaId, v, true);
        }
        return detalhar(empresaId, v);
    }

    public ViagemResponse finalizar(Long id, FinalizacaoRequest req) {
        Long empresaId = logado.empresaId();
        Viagem v = travarComCaminhao(id, empresaId);
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito("A viagem já está finalizada.");
        }
        if (v.isCancelada()) {
            throw RegraNegocioException.conflito("Viagem cancelada não pode ser finalizada.");
        }

        Estado estado = new Estado(v.getDataInicio(), req.dataFim(), v.getValorFrete(), v.getOrigem(),
                v.getDestino(), v.getKmInicialVazio(), v.getKmInicialCarregado(), req.kmFinalCarregado(),
                req.consumoMedioVazio() != null ? req.consumoMedioVazio() : v.getConsumoMedioVazio(),
                req.consumoMedioCarregado() != null ? req.consumoMedioCarregado() : v.getConsumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        estado.validarCompleta();
        validarDatasDosLancamentos(v, empresaId, estado);
        exigirSemSobreposicao(empresaId, v.getCaminhao().getId(), estado, v.getId());
        exigirSeguinteCompativel(empresaId, v.getCaminhao().getId(), estado, v.getId());

        estado.aplicar(v);
        v.setStatus(StatusViagem.FINALIZADA);
        v.setFinalizadaEm(agora());
        v.marcarAlteracao();
        consolidar(empresaId, v, true);
        return detalhar(empresaId, v);
    }

    /**
     * Cancela uma viagem em andamento (só DONO; o controller exige o perfil).
     * Nada é apagado: a viagem fica CANCELADA com motivo, autor e momento, os
     * lançamentos continuam, e o período é fechado na data final informada
     * (padrão: hoje) para liberar o caminhão. O resultado é consolidado como
     * no fechamento de um frete, com a mesma calculadora; sem dados completos,
     * o custo fica incompleto, nunca zero.
     *
     * Viagem já finalizada não é cancelada: correções dela seguem pelo PUT.
     */
    public ViagemResponse cancelar(Long id, CancelamentoRequest req) {
        Long empresaId = logado.empresaId();
        Viagem v = travarComCaminhao(id, empresaId);
        if (v.isCancelada()) {
            throw RegraNegocioException.conflito("A viagem já está cancelada.");
        }
        if (v.isFinalizada()) {
            throw RegraNegocioException.conflito(
                    "Viagem finalizada não pode ser cancelada. Para ajustar valores, corrija a viagem.");
        }
        String motivo = texto(req.motivo());
        if (motivo == null) {
            throw RegraNegocioException.invalido("Informe o motivo do cancelamento.");
        }

        Estado estado = new Estado(v.getDataInicio(),
                req.dataFim() != null ? req.dataFim() : hoje(),
                v.getValorFrete(), v.getOrigem(), v.getDestino(),
                v.getKmInicialVazio(), v.getKmInicialCarregado(), req.kmFinalCarregado(),
                req.consumoMedioVazio() != null ? req.consumoMedioVazio() : v.getConsumoMedioVazio(),
                req.consumoMedioCarregado() != null ? req.consumoMedioCarregado() : v.getConsumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        validarDatasDosLancamentos(v, empresaId, estado);
        exigirSemSobreposicao(empresaId, v.getCaminhao().getId(), estado, v.getId());
        exigirSeguinteCompativel(empresaId, v.getCaminhao().getId(), estado, v.getId());

        estado.aplicar(v);
        v.cancelar(usuarios.getReferenceById(logado.usuarioId()), motivo, agora());
        v.marcarAlteracao();
        consolidar(empresaId, v, true);
        return detalhar(empresaId, v);
    }

    /**
     * Grava a fotografia financeira do frete, calculada agora com a política
     * atual. Afeta apenas esta viagem: nenhum outro frete finalizado é
     * recalculado.
     *
     * reavaliarPreco: true na finalização, no cadastro histórico, na correção
     * da viagem e na correção de diesel (a referência de preço é buscada de
     * novo); false na correção de Arla, pedágio ou despesa extra (mantém o
     * preço já congelado e recalcula o restante).
     *
     * A fotografia anterior, se existir, é arquivada antes (AuditoriaViagem).
     */
    void consolidar(Long empresaId, Viagem v, boolean reavaliarPreco) {
        // Numa correção, a fotografia que vai ser substituída fica guardada.
        auditoria.arquivarFotografia(v);
        viagens.flush();
        LimitesPlausibilidade limites = politica.limites();
        Instant agora = agora();
        if (reavaliarPreco || v.referenciaConsolidada() == null) {
            v.consolidarReferencia(rentabilidade.precoAtual(empresaId, v, limites), agora);
        }
        v.consolidarResultado(rentabilidade.recalcular(empresaId, v, limites), agora);
    }

    /** Instante em microssegundos, a precisão do PostgreSQL: o valor não muda ao recarregar. */
    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Busca com bloqueio de escrita; usada por toda operação que altera a viagem ou seus lançamentos. */
    Viagem travar(Long id, Long empresaId) {
        return viagens.travarPorIdEEmpresa(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
    }

    /**
     * Bloqueia o caminhão da viagem e depois a própria viagem, sempre nessa ordem
     * (a mesma da abertura), para que a checagem de sobreposição de períodos não
     * concorra com outra abertura ou alteração de datas no mesmo caminhão.
     */
    Viagem travarComCaminhao(Long id, Long empresaId) {
        Long caminhaoId = viagens.caminhaoDaViagem(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
        caminhoes.travarPorIdEEmpresa(caminhaoId, empresaId).orElseThrow(ViagemService::naoEncontrada);
        Viagem v = travar(id, empresaId);
        exigirAcesso(v);
        return v;
    }

    /** Travar + conferir que o usuário logado enxerga a viagem (MOTORISTA: caminhão vinculado). */
    Viagem travarAcessivel(Long id, Long empresaId) {
        Viagem v = travar(id, empresaId);
        exigirAcesso(v);
        return v;
    }

    /** MOTORISTA só alcança viagens de caminhões vinculados a ele; as demais respondem 404. */
    void exigirAcesso(Viagem v) {
        if (!acesso.podeVer(v.getCaminhao())) {
            throw naoEncontrada();
        }
    }

    /** Depois de encerrada (finalizada ou cancelada), a viagem só é corrigida pelo DONO. */
    void exigirAlteravelPeloPerfil(Viagem v) {
        if (logado.isMotorista() && v.getStatus() != StatusViagem.EM_ANDAMENTO) {
            throw RegraNegocioException.proibido(
                    "Viagem encerrada não pode ser alterada pelo motorista. Correções são feitas pelo dono.");
        }
    }

    /**
     * Motorista responsável pela viagem.
     *
     * MOTORISTA logado: é sempre ele mesmo (informar outro é recusado).
     * DONO: pode deixar sem motorista, indicar a si mesmo ou outro usuário da
     * empresa com perfil MOTORISTA ou DONO. Em viagem que está acontecendo
     * (operacional = true) o indicado precisa estar ativo e, se MOTORISTA,
     * vinculado ao caminhão. Em viagem já encerrada (histórica ou correção)
     * basta ser usuário da empresa: o vínculo pode ter mudado desde então.
     */
    private Usuario resolverMotorista(Long empresaId, Caminhao caminhao, Long motoristaId, boolean operacional) {
        if (logado.isMotorista()) {
            if (motoristaId != null && !motoristaId.equals(logado.usuarioId())) {
                throw RegraNegocioException.proibido("O motorista só abre viagem em seu próprio nome.");
            }
            return usuarios.getReferenceById(logado.usuarioId());
        }
        if (motoristaId == null) {
            return null;
        }
        Usuario u = usuarios.findByIdAndEmpresaId(motoristaId, empresaId)
                .orElseThrow(() -> RegraNegocioException.naoEncontrado("Motorista não encontrado."));
        if (operacional) {
            if (!u.isAtivo()) {
                throw RegraNegocioException.conflito("O motorista indicado não está ativo.");
            }
            if (u.getPerfil() == Perfil.MOTORISTA
                    && !vinculos.existsByMotoristaIdAndCaminhaoIdAndCaminhaoEmpresaId(u.getId(), caminhao.getId(), empresaId)) {
                throw RegraNegocioException.conflito("O motorista indicado não está vinculado a este caminhão.");
            }
        }
        return u;
    }

    /**
     * Um caminhão não pode ter duas viagens com períodos sobrepostos. Datas
     * inclusivas, com uma única exceção: uma viagem pode começar no dia em que
     * a anterior termina (descarga e saída vazia para o próximo frete no mesmo
     * dia). Duas viagens não podem começar no mesmo dia. Viagem sem data final
     * tem período aberto. Deve ser chamado com o caminhão já bloqueado.
     */
    private void exigirSemSobreposicao(Long empresaId, Long caminhaoId, Estado estado, Long ignorarId) {
        if (viagens.existeSobreposicao(empresaId, caminhaoId, estado.dataInicio(), estado.dataFim(), ignorarId)) {
            throw RegraNegocioException.conflito(
                    "O período desta viagem se sobrepõe ao de outra viagem do mesmo caminhão."
                    + " Duas viagens do caminhão só podem compartilhar o dia em que uma termina e a outra"
                    + " começa, e não podem começar no mesmo dia.");
        }
    }

    static RegraNegocioException naoEncontrada() {
        return RegraNegocioException.naoEncontrado("Viagem não encontrada.");
    }

    static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    /** Na visão do MOTORISTA, a rentabilidade (custos, lucro, margem) não é enviada. */
    private ViagemResponse detalhar(Long empresaId, Viagem v) {
        viagens.flush();
        return ViagemResponse.de(v,
                abastecimentos.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(v.getId(), empresaId),
                despesas.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(v.getId(), empresaId),
                logado.isMotorista() ? null : rentabilidade.calcular(empresaId, v));
    }

    /**
     * Pedágios e despesas precisam estar entre a data de início e a data final
     * da viagem. Abastecimentos também, ou antes do início, a partir do fim da
     * viagem anterior do caminhão (RegrasLancamento.validarDataAbastecimento).
     * Chamado quando as datas da viagem mudam, com o estado candidato.
     */
    private void validarDatasDosLancamentos(Viagem v, Long empresaId, Estado estado) {
        boolean despesaForaDoPeriodo = despesas.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(
                        v.getId(), empresaId).stream()
                .map(Despesa::getData)
                .anyMatch(data -> data.isBefore(estado.dataInicio())
                        || (estado.dataFim() != null && data.isAfter(estado.dataFim())));
        List<Abastecimento> lista =
                abastecimentos.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(v.getId(), empresaId);
        boolean abastecimentoDepoisDoFim = lista.stream()
                .anyMatch(a -> estado.dataFim() != null && a.getData().isAfter(estado.dataFim()));
        if (despesaForaDoPeriodo || abastecimentoDepoisDoFim) {
            throw RegraNegocioException.invalido(
                    "Há abastecimentos ou despesas com data fora do período da viagem.");
        }
        if (lista.stream().anyMatch(a -> a.getData().isBefore(estado.dataInicio()))) {
            LocalDate fimAnterior = fimDaAnterior(empresaId, v.getCaminhao().getId(), estado.dataInicio(), v.getId());
            for (Abastecimento a : lista) {
                RegrasLancamento.validarDataAbastecimento(estado.dataInicio(), estado.dataFim(), fimAnterior,
                        a.getData(), null);
            }
        }
    }

    /** Data final da viagem do caminhão que começou imediatamente antes de inicio; nula se não houver. */
    private LocalDate fimDaAnterior(Long empresaId, Long caminhaoId, LocalDate inicio, Long ignorarId) {
        return viagens.anterior(empresaId, caminhaoId, inicio, ignorarId).map(Viagem::getDataFim).orElse(null);
    }

    /** Data de um abastecimento novo ou corrigido da viagem (usado por LancamentoService). */
    void validarDataAbastecimento(Viagem v, Long empresaId, LocalDate data, LocalTime hora) {
        LocalDate fimAnterior = data.isBefore(v.getDataInicio())
                ? fimDaAnterior(empresaId, v.getCaminhao().getId(), v.getDataInicio(), v.getId())
                : null;
        RegrasLancamento.validarDataAbastecimento(v.getDataInicio(), v.getDataFim(), fimAnterior, data, hora);
    }

    /**
     * A viagem seguinte do caminhão pode ter abastecimentos feitos antes do
     * início dela, depois do fim desta. A data final desta viagem não pode
     * passar deles: senão esses abastecimentos ficariam dentro do período
     * desta, associados à viagem errada. Deve ser chamado com o caminhão
     * bloqueado e depois da checagem de sobreposição.
     */
    private void exigirSeguinteCompativel(Long empresaId, Long caminhaoId, Estado estado, Long ignorarId) {
        if (estado.dataFim() == null) {
            return;
        }
        viagens.seguinte(empresaId, caminhaoId, estado.dataInicio(), ignorarId)
                .filter(seguinte -> abastecimentos.existsByViagemIdAndViagemEmpresaIdAndDataBefore(
                        seguinte.getId(), empresaId, estado.dataFim()))
                .ifPresent(seguinte -> {
                    throw RegraNegocioException.conflito("A viagem seguinte do caminhão tem abastecimento"
                            + " feito antes do início dela, com data anterior à data final informada. A data"
                            + " final desta viagem não pode passar desse abastecimento.");
                });
    }

    /**
     * Estado candidato da viagem. É validado antes de tocar na entidade, para
     * que uma requisição recusada nunca deixe a viagem alterada pela metade.
     */
    private record Estado(
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal valorFrete,
            String origem,
            String destino,
            Integer kmInicialVazio,
            Integer kmInicialCarregado,
            Integer kmFinalCarregado,
            BigDecimal consumoMedioVazio,
            BigDecimal consumoMedioCarregado) {

        /** Coerência das datas e da quilometragem, em qualquer estado da viagem. */
        void validarCoerencia(LimitesPlausibilidade limites) {
            if (dataInicio.isAfter(hoje())) {
                throw RegraNegocioException.invalido("A data de início não pode estar no futuro.");
            }
            if (dataFim != null) {
                if (dataFim.isBefore(dataInicio)) {
                    throw RegraNegocioException.invalido("A data final não pode ser anterior à data de início.");
                }
                if (dataFim.isAfter(hoje())) {
                    throw RegraNegocioException.invalido("A data final não pode estar no futuro.");
                }
            }
            if (kmInicialVazio == null && kmInicialCarregado == null) {
                throw RegraNegocioException.invalido(
                        "Informe a quilometragem inicial: do deslocamento vazio ou do carregamento.");
            }
            if (kmInicialVazio != null && kmInicialCarregado != null && kmInicialCarregado <= kmInicialVazio) {
                throw RegraNegocioException.invalido(
                        "A quilometragem do carregamento deve ser maior que a da saída do deslocamento vazio.");
            }
            if (kmFinalCarregado != null) {
                if (kmInicialCarregado == null) {
                    throw RegraNegocioException.invalido(
                            "Informe a quilometragem do carregamento antes da quilometragem final.");
                }
                if (kmFinalCarregado <= kmInicialCarregado) {
                    throw RegraNegocioException.invalido(
                            "A quilometragem final deve ser maior que a do carregamento.");
                }
            }
            if (kmInicialVazio == null && consumoMedioVazio != null) {
                throw RegraNegocioException.invalido("Consumo médio vazio informado sem deslocamento vazio.");
            }
            exigirConsumoPlausivel(consumoMedioVazio, "vazio", limites);
            exigirConsumoPlausivel(consumoMedioCarregado, "carregado", limites);
        }

        private static void exigirConsumoPlausivel(BigDecimal consumo, String nome, LimitesPlausibilidade limites) {
            if (consumo != null && !limites.consumoValido(consumo)) {
                throw RegraNegocioException.invalido("Consumo médio " + nome + " fora do limite de plausibilidade: "
                        + "informe de " + limites.faixaConsumo() + ".");
            }
        }

        /** Dados obrigatórios de um frete finalizado. Lista tudo o que falta de uma vez. */
        void validarCompleta() {
            List<String> faltando = new ArrayList<>();
            if (valorFrete == null) faltando.add("valor do frete");
            if (origem == null) faltando.add("origem");
            if (destino == null) faltando.add("destino");
            if (dataFim == null) faltando.add("data final");
            if (kmInicialCarregado == null) faltando.add("quilometragem do carregamento");
            if (kmFinalCarregado == null) faltando.add("quilometragem final");
            if (consumoMedioCarregado == null) faltando.add("consumo médio carregado");
            if (kmInicialVazio != null && consumoMedioVazio == null) faltando.add("consumo médio vazio");
            if (!faltando.isEmpty()) {
                throw RegraNegocioException.invalido(
                        "Para finalizar a viagem, informe: " + String.join(", ", faltando) + ".");
            }
        }

        void aplicar(Viagem v) {
            v.setDataInicio(dataInicio);
            v.setDataFim(dataFim);
            v.setValorFrete(valorFrete);
            v.setOrigem(origem);
            v.setDestino(destino);
            v.setKmInicialVazio(kmInicialVazio);
            v.setKmInicialCarregado(kmInicialCarregado);
            v.setKmFinalCarregado(kmFinalCarregado);
            v.setConsumoMedioVazio(consumoMedioVazio);
            v.setConsumoMedioCarregado(consumoMedioCarregado);
        }
    }

    static String texto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
