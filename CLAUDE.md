# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Frota+ é a API REST de um sistema multiempresa de gestão financeira para transporte rodoviário de cargas. Responda em português.

- **Código e Git:** fonte de verdade sobre o que existe.
- **`docs/CONTEXTO_FROTA_MAIS.md`:** contexto técnico, roadmap (seção 6.6) e pendências (Anexo A); a seção 0.4 resume o estado de 11/11/2026 e prevalece sobre trechos antigos. Também é a referência de regras de negócio quando o documento privado abaixo não existir no clone. Leia as seções relevantes antes de tocar em um domínio. Se divergir do código, reporte; não ajuste um para coincidir com o outro.
- **`docs/API.md`:** endpoints, permissões e códigos de resposta. Atualize junto com qualquer mudança de contrato.
- **`contexto-local/`:** privado, ignorado pelo Git e presente só em alguns clones. Nunca copie o conteúdo para arquivos versionados. O repositório é **público**.
- **`contexto-local/REGRAS_DE_NEGOCIO_FROTA_MAIS.md`:** se existir neste clone, leia antes de qualquer trabalho que envolva regras de negócio. Nesse caso, ele é a fonte de verdade das decisões de negócio e prevalece sobre regras mais antigas deste arquivo e do `docs/CONTEXTO_FROTA_MAIS.md`, que continua valendo para arquitetura, implementação e roadmap técnico. Regras que o documento marca como pendentes precisam ser discutidas com o proprietário antes de qualquer implementação.

## Comandos

### Ambientes

- **Principal:** GitHub Codespaces (Linux, Java 21, PostgreSQL 16 em `localhost:5432`). É onde se desenvolve, executa o backend, faz build e roda testes.
- **Auxiliar:** notebook Windows. Serve para usar o Claude Code, ler documentos, planejar e alterar arquivos quando necessário. Tem Java 17, e o projeto exige Java 21. **Não presuma que ele executa ou testa o backend.**
- **Sincronização:** GitHub. Não trabalhe nos dois clones ao mesmo tempo sem sincronizar. Antes de começar em um deles, confira `git status` e se ele está atualizado com `origin/main`.

**Não prepare o notebook:** não atualize o Java, não instale PostgreSQL ou outras ferramentas e não configure credenciais nele. Quando uma tarefa exigir build ou testes, use o Codespaces, respeitando as regras abaixo. Se estiver no notebook, informe o que precisa ser rodado lá e não afirme que foi verificado.

Comandos do Maven Wrapper. Execute a coluna do Codespaces; a coluna Windows é só referência, enquanto o notebook não estiver preparado:

| Ação | Linux (Codespaces) | Windows (PowerShell) |
|---|---|---|
| Validar o `pom.xml` | `./mvnw validate` | `.\mvnw.cmd validate` |
| Build sem testes | `./mvnw clean package -DskipTests` | `.\mvnw.cmd clean package -DskipTests` |
| Subir a API na 8080 | `./run-dev-frotamais.sh` | `$env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run` |
| Testar uma classe | `./mvnw test -Dtest=SegurancaCaminhaoTest` | `.\mvnw.cmd test -Dtest=SegurancaCaminhaoTest` |
| Testar um método | `./mvnw test -Dtest=SegurancaCaminhaoTest#deveBloquearListagemSemToken` | `.\mvnw.cmd test -Dtest=SegurancaCaminhaoTest#deveBloquearListagemSemToken` |
| Toda a suíte | `./mvnw test` | `.\mvnw.cmd test` |

**Scripts `.sh`** rodam apenas em ambiente compatível com Bash, como o Codespaces, Git Bash ou WSL. Não funcionam no PowerShell nem no `cmd`. `run-dev-frotamais.sh` carrega `.frotamais-jwt.env`, exige `JWT_SECRET` e ativa o perfil `dev` (credenciais locais do banco em `application-dev.properties`); sem o script, `JWT_SECRET` e o perfil precisam estar definidos na sessão. Sem perfil, `DB_URL`, `DB_USER` e `DB_PASSWORD` são obrigatórias (configuração de produção).

Não há linter configurado.

### Testes e banco de dados

Todos os testes atuais sobem o contexto completo contra o banco exclusivo `frotamais_test_flyway`, nunca contra `frotamais` (nem o antigo `frotamais_test`, que não é mais usado). Precisam do Postgres ligado e do banco criado vazio (`CREATE DATABASE frotamais_test_flyway`); as migrations montam o schema, como no CI. `src/test/resources/config/application.properties` fixa a URL, e `GuardaBancoDeTeste` interrompe a execução se a conexão efetiva for outra. E-mails são capturados por `suporte/CaixaDeSaidaTeste`; nenhum teste usa SMTP.

- **Não enfraqueça esse isolamento:** não remova o guarda nem aponte os testes para `frotamais`.
- **Não execute sem autorização explícita** testes que gravam dados permanentes no banco de desenvolvimento.
- **Testes novos que gravam dados** devem usar `@Transactional`, para desfazer os registros ao final. Os testes de viagens herdam de `viagem/ApiViagemTeste`, que já faz isso e usa tokens reais.
- **Única exceção autorizada:** `viagem/ConcorrenciaViagemTest` desliga a transação do teste para gravar de verdade com requisições simultâneas, e no `@AfterEach` apaga só os registros da empresa que ele mesmo criou, depois de conferir `current_database()`. Não copie esse padrão sem nova autorização.
- **Armadilha do `@Transactional` no teste:** o serviço participa da transação do teste, então nada é gravado no banco até o fim do teste, e uma requisição recusada não sofre rollback antes da próxima chamada. Por isso os serviços validam antes de alterar a entidade. Erros que só aparecem na gravação, como uma FK violada, não são exercitados.

### `pom.xml`

O byte inicial antes de `<?xml`, que tornava o arquivo XML inválido, foi removido no commit `1d2b4bb`. O arquivo corrigido passou no Maven 3.9.9 e no 3.10.0, com LF e com CRLF. O Maven tolera no máximo 1 byte antes da declaração, e `\r\n` já são 2 (seção 6.3 do documento de contexto). Se aparecer de novo um erro de parsing como `Non-parseable POM`, investigue primeiro os bytes iniciais: `head -c 6 pom.xml | od -c`.

## Arquitetura

Monólito Spring Boot 4.1.1 na raiz do repositório, com pacotes por funcionalidade em `br.com.frotamais`. Cada funcionalidade segue Controller → Service → Repository, com entidade JPA separada de DTOs `record` (`XRequest`/`XResponse`) e nomes de domínio em português. Use `caminhao/` como molde.

**Fluxo de autenticação (atravessa `seguranca/` e `auth/`):**
1. `AuthService` valida a senha com BCrypt e `TokenService` emite um JWT HS256 com `sub`, `empresaId` e `perfil`.
2. A cada requisição, o `conversor()` de `SecurityConfig` recarrega o usuário do banco, exige que esteja ativo e compara o `empresaId` do token com o do banco.
3. As authorities `ROLE_*` vêm **do banco**, então desativar um usuário ou mudar o perfil vale na hora. A claim `perfil` não é usada para autorizar.

**Isolamento por empresa:** os serviços obtêm o `empresaId` de `UsuarioLogado`, nunca do JSON, e consultam por métodos como `findByIdAndEmpresaId`. Recurso de outra empresa responde `404`. Isso está garantido nos repositórios de `caminhao/`, `motorista/`, `manutencao/` e `viagem/` (a ativação de convite é pública e localiza o convite só pelo hash do token); nos lançamentos e na auditoria, o filtro é `viagem.empresa.id`. `JpaRepository` herda `findById`, `findAll` e `deleteById` sem filtro, então o compilador não protege você. Toda funcionalidade nova precisa de isolamento verificável em todas as consultas e operações. A entidade pode ter vínculo direto com a empresa ou pertencer a outra entidade que tenha esse vínculo, desde que a segurança seja garantida e comprovada por teste de isolamento com tokens reais.

**Acesso do MOTORISTA:** só aos caminhões vinculados a ele (`motorista_caminhoes`) e às viagens desses caminhões, conferido em `caminhao/AcessoCaminhao` e `ViagemService.exigirAcesso` (sem vínculo → `404`). Não vê custo, lucro, margem, indicadores, auditoria nem manutenções; não altera viagem encerrada, não cadastra histórica e não cancela. `@PreAuthorize` separa os perfis nos controllers; o vínculo é checado nos serviços.

**Erros:** um formato só, `{"status","erro","detalhes"}`, em `comum/ApiExceptionHandler` e `comum/RespostasSeguranca` (`401`/`403`). Código novo lança `RegraNegocioException` (`invalido`, `naoEncontrado`, `conflito`, `proibido`, `naoAutenticado`, `indisponivel`); não use `ResponseStatusException`.

**Schema:** versionado pelo Flyway (`src/main/resources/db/migration`), com `ddl-auto=validate`. Toda mudança de entidade exige uma migration nova (`V{n+1}__...`); nunca edite uma já aplicada. Valor novo em enum persistido exige atualizar o `CHECK` na migration. Bancos antigos e adoção do Flyway: `docs/MIGRATIONS.md` (o `frotamais` do Codespaces ainda não foi adequado; isso exige autorização).

**Viagens (`viagem/`):** cada frete é uma viagem, com deslocamento vazio opcional atribuído a ele. As fórmulas financeiras ficam só em `CalculadoraRentabilidade`, uma função pura testada sem banco. Não some os valores pagos no diesel como custo: eles só definem o preço médio. Sem referência de preço, o custo fica nulo, nunca zero. Detalhes em `docs/CONTEXTO_FROTA_MAIS.md`, seções 0.4 e 4.9.

Invariantes que não podem ser quebrados:
- **Períodos:** duas viagens do mesmo caminhão não se sobrepõem (datas inclusivas; em andamento = período aberto), e a checagem roda com o caminhão bloqueado. Única data compartilhável: o dia em que uma termina e a seguinte começa. Duas viagens não começam no mesmo dia, e por isso a data de início ordena as viagens do caminhão.
- **Ordem de bloqueio:** sempre caminhão → viagem.
- **Fotografia financeira:** frete encerrado (finalizado ou cancelado) devolve o resultado e a política de cálculo gravados no encerramento (`ResultadoConsolidado`), sem recalcular. Só uma correção explícita dele regrava a fotografia, com a política atual (`ViagemService.consolidar`), e a versão anterior é arquivada (`AuditoriaViagem`). Nunca recalcule outros fretes encerrados. Mudou uma fórmula? Troque `CalculadoraRentabilidade.VERSAO_POLITICA`.
- **Auditoria:** toda correção de viagem encerrada (da viagem ou de um lançamento) e toda inclusão em viagem cancelada passam por `AuditoriaViagem.registrar`, com o retrato dos campos antes e depois e o perfil de quem alterou. As tabelas de auditoria só recebem inclusões.
- **Preço de referência:** só de viagem que começou antes; nunca de uma posterior (vale para a viagem histórica).
- **Plausibilidade:** os limites ficam em `app.viagens.limites.*` (`LimitesPlausibilidade`), lidos a cada operação por `PoliticaCalculo.limites()`. Não espalhe constantes pelo código. Arla não tem limite de preço nem de tanque.
- **Viagem histórica** (`POST /api/viagens/historicas`): nasce finalizada, numa transação, com tudo validado antes de gravar.
- **Viagem encerrada:** finalizada não aceita `POST` de lançamento; cancelada aceita só do DONO (auditado, regrava a fotografia dela). Nenhuma aceita `DELETE` de lançamento, e o MOTORISTA não altera nenhuma. Cancelar é só do DONO, com motivo, a partir de viagem em andamento; a `dataFim` da cancelada é a data do cancelamento.
- **Canceladas no financeiro:** entram pelo mês da data do cancelamento, em bloco separado nos indicadores, e somam no `resultadoFinanceiroPeriodo`; nunca nos indicadores de desempenho dos fretes concluídos.
- **Abastecimento entre viagens:** pode ser anterior ao início da viagem, a partir do dia em que a anterior do caminhão terminou; pertence à seguinte. A data final da anterior não pode passar desses abastecimentos (`exigirSeguinteCompativel`). Sem viagem anterior (primeiro frete): aceito, sem limite de dias. A abertura aceita a lista `abastecimentos`, na mesma transação.
- **Despesa extra:** descrição livre. Não bloqueie palavras (alimentação inclusive) nem crie categorias ou classificação automática sem decisão de negócio.
- **Caminhão:** com viagem aberta não é inativado nem excluído. A exclusão é lógica (`EXCLUIDO`); caminhão excluído não entra em viagens novas nem recebe manutenções (só viagem histórica que terminou antes da exclusão), e seu histórico fica intacto.
- **Manutenção:** pertence ao caminhão, nunca a um frete; entra só nos indicadores do período e não altera nenhuma viagem.

**Spring Boot 4:** Jackson é 3.x (`tools.jackson.databind`), `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure` e `include-message` é `spring.web.error.*`. Não troque imports por memória do Boot 3.

**Testes:** `jwt()` do `spring-security-test` contorna o `conversor()`. Um teste com ele prova o `@PreAuthorize`, não o fluxo real.

**Lacunas conhecidas:** não há rate limit em login, registro e ativação; CORS não configurado (depende do Angular); o `frotamais` do Codespaces ainda não adotou o Flyway. O e-mail é único em todo o sistema.

## Modo de trabalho

**Autonomia:** tarefas pequenas e conhecidas, como corrigir um bug localizado, ajustar um teste ou escrever documentação dentro de um padrão existente. Faça, verifique e relate.

**Planejar e explicar antes de alterar:** funcionalidades novas, mudanças de arquitetura, modelo de dados, autenticação, autorização ou dependências. Apresente o diagnóstico e a proposta e aguarde aprovação. Se a tarefa depender de uma pendência do Anexo A, pergunte em vez de escolher.

**Sempre com autorização explícita:**
- Commit e push.
- Qualquer operação destrutiva no banco: `DROP`, `TRUNCATE`, `DELETE`, recriar schema ou volume, alterar credenciais.
- Alterar regras de negócio confirmadas ou permissões dos perfis.

**Proteção de dados:**
- Nunca leia, imprima ou copie `.frotamais-jwt.env`.
- Ao consultar `usuarios`, não selecione `senha_hash`.
- Mantenha a porta 5432 privada.
- Não enfraqueça a validação do JWT, a checagem de usuário ativo, a comparação de `empresaId` ou os `@PreAuthorize`, nem mesmo para um teste passar.
- Antes de commitar, confira se nada de `contexto-local/` entrou no conteúdo.

**Antes de considerar concluído:**
- Rode as verificações que a mudança exige e informe o que rodou e o que não rodou.
- Não afirme comportamento sem evidência. Separe o verificado do presumido.
- Antes de alterar código existente, confira `git status` e `git diff`.
- Mantenha uma única instância do back-end na porta 8080.

**Commits:** separados por funcionalidade, no estilo `feat(auth): ...`, `feat(caminhoes): ...`, `chore(security): ...`, `test: ...`.

**Modo didático:** o responsável pelo projeto está aprendendo a stack. Em funcionalidades novas ou quando o tema for aprendizado, explique o porquê das decisões sobre Java, Spring Boot, segurança e arquitetura, com exemplos do domínio e comandos completos, prontos para colar. Em tarefas pequenas, seja breve.
