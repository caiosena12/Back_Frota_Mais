package br.com.frotamais.viagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.frotamais.caminhao.Caminhao;
import br.com.frotamais.caminhao.CaminhaoRepository;
import br.com.frotamais.comum.Datas;
import br.com.frotamais.comum.RegraNegocioException;
import br.com.frotamais.empresa.EmpresaRepository;
import br.com.frotamais.seguranca.UsuarioLogado;
import br.com.frotamais.usuario.Usuario;
import br.com.frotamais.usuario.UsuarioRepository;

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

    public ViagemService(ViagemRepository viagens, AbastecimentoRepository abastecimentos,
            DespesaRepository despesas, CaminhaoRepository caminhoes, EmpresaRepository empresas,
            UsuarioRepository usuarios, UsuarioLogado logado, RentabilidadeService rentabilidade,
            PoliticaCalculo politica) {
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
        List<Viagem> lista = viagens.listar(empresaId, status, caminhaoId);
        Map<Long, RentabilidadeResponse> calculos = rentabilidade.calcular(empresaId, lista);
        return lista.stream().map(v -> ViagemResumoResponse.de(v, calculos.get(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ViagemResponse buscar(Long id) {
        Long empresaId = logado.empresaId();
        return detalhar(empresaId, viagens.findByIdAndEmpresaId(id, empresaId).orElseThrow(ViagemService::naoEncontrada));
    }

    public ViagemResponse criar(ViagemCriacaoRequest req) {
        Long empresaId = logado.empresaId();

        Caminhao caminhao = travarCaminhaoParaAbertura(req.caminhaoId(), empresaId);
        if (viagens.existsByEmpresaIdAndCaminhaoIdAndStatus(empresaId, caminhao.getId(), StatusViagem.EM_ANDAMENTO)) {
            throw RegraNegocioException.conflito(
                    "Este caminhão já tem uma viagem em andamento. Finalize-a antes de abrir outra.");
        }

        Estado estado = new Estado(req.dataInicio(), null, req.valorFrete(), texto(req.origem()),
                texto(req.destino()), req.kmInicialVazio(), req.kmInicialCarregado(), null,
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        exigirSemSobreposicao(empresaId, caminhao.getId(), estado, null);

        Viagem v = nova(empresaId, caminhao);
        estado.aplicar(v);
        viagens.save(v);
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

        Estado estado = new Estado(req.dataInicio(), req.dataFim(), req.valorFrete(), texto(req.origem()),
                texto(req.destino()), req.kmInicialVazio(), req.kmInicialCarregado(), req.kmFinalCarregado(),
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(limites);
        estado.validarCompleta();
        for (AbastecimentoRequest a : req.abastecimentosOuVazio()) {
            RegrasLancamento.validarData(estado.dataInicio(), estado.dataFim(), a.data());
            RegrasLancamento.validarAbastecimento(caminhao, a, limites);
        }
        for (PedagioRequest p : req.pedagiosOuVazio()) {
            RegrasLancamento.validarData(estado.dataInicio(), estado.dataFim(), p.data());
        }
        for (DespesaExtraRequest d : req.despesasExtrasOuVazio()) {
            RegrasLancamento.validarData(estado.dataInicio(), estado.dataFim(), d.data());
        }
        exigirSemSobreposicao(empresaId, caminhao.getId(), estado, null);

        Instant agora = agora();
        Viagem v = nova(empresaId, caminhao);
        estado.aplicar(v);
        v.setStatus(StatusViagem.FINALIZADA);
        v.setFinalizadaEm(agora);
        v.setCadastroRetroativoEm(agora);
        viagens.save(v);

        Usuario autor = v.getCriadoPor();
        for (AbastecimentoRequest item : req.abastecimentosOuVazio()) {
            Abastecimento a = new Abastecimento();
            a.setViagem(v);
            a.setCriadoPor(autor);
            a.setTipo(item.tipo());
            a.setData(item.data());
            a.setLitros(item.litros());
            a.setValorTotal(item.valorTotal());
            abastecimentos.save(a);
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

        if (!v.isFinalizada() && (req.dataFim() != null || req.kmFinalCarregado() != null)) {
            throw RegraNegocioException.invalido(
                    "Data final e quilometragem final são informadas na finalização da viagem.");
        }

        Estado estado = new Estado(req.dataInicio(),
                v.isFinalizada() ? req.dataFim() : null,
                req.valorFrete(), texto(req.origem()), texto(req.destino()),
                req.kmInicialVazio(), req.kmInicialCarregado(),
                v.isFinalizada() ? req.kmFinalCarregado() : null,
                req.consumoMedioVazio(), req.consumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        if (v.isFinalizada()) {
            // Correção de viagem finalizada: ela precisa continuar completa e coerente.
            estado.validarCompleta();
        }
        validarDatasDosLancamentos(v.getId(), empresaId, estado);
        exigirSemSobreposicao(empresaId, v.getCaminhao().getId(), estado, v.getId());

        estado.aplicar(v);
        v.marcarAlteracao();
        if (v.isFinalizada()) {
            // Correção explícita de um frete finalizado: regrava a fotografia só dele,
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

        Estado estado = new Estado(v.getDataInicio(), req.dataFim(), v.getValorFrete(), v.getOrigem(),
                v.getDestino(), v.getKmInicialVazio(), v.getKmInicialCarregado(), req.kmFinalCarregado(),
                req.consumoMedioVazio() != null ? req.consumoMedioVazio() : v.getConsumoMedioVazio(),
                req.consumoMedioCarregado() != null ? req.consumoMedioCarregado() : v.getConsumoMedioCarregado());
        estado.validarCoerencia(politica.limites());
        estado.validarCompleta();
        validarDatasDosLancamentos(v.getId(), empresaId, estado);
        exigirSemSobreposicao(empresaId, v.getCaminhao().getId(), estado, v.getId());

        estado.aplicar(v);
        v.setStatus(StatusViagem.FINALIZADA);
        v.setFinalizadaEm(agora());
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
     */
    void consolidar(Long empresaId, Viagem v, boolean reavaliarPreco) {
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
    private Viagem travarComCaminhao(Long id, Long empresaId) {
        Long caminhaoId = viagens.caminhaoDaViagem(id, empresaId).orElseThrow(ViagemService::naoEncontrada);
        caminhoes.travarPorIdEEmpresa(caminhaoId, empresaId).orElseThrow(ViagemService::naoEncontrada);
        return travar(id, empresaId);
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

    private ViagemResponse detalhar(Long empresaId, Viagem v) {
        viagens.flush();
        return ViagemResponse.de(v,
                abastecimentos.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(v.getId(), empresaId),
                despesas.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(v.getId(), empresaId),
                rentabilidade.calcular(empresaId, v));
    }

    /** Todos os lançamentos precisam estar entre a data de início e a data final da viagem. */
    private void validarDatasDosLancamentos(Long viagemId, Long empresaId, Estado estado) {
        boolean foraDoPeriodo = Stream.concat(
                abastecimentos.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(viagemId, empresaId)
                        .stream().map(Abastecimento::getData),
                despesas.findAllByViagemIdAndViagemEmpresaIdOrderByDataAscIdAsc(viagemId, empresaId)
                        .stream().map(Despesa::getData))
                .anyMatch(data -> data.isBefore(estado.dataInicio())
                        || (estado.dataFim() != null && data.isAfter(estado.dataFim())));
        if (foraDoPeriodo) {
            throw RegraNegocioException.invalido(
                    "Há abastecimentos ou despesas com data fora do período da viagem.");
        }
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
