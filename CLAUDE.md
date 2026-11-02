# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Frota+ é a API REST de um sistema multiempresa de gestão financeira para transporte rodoviário de cargas. Responda em português.

- **Código e Git:** fonte de verdade sobre o que existe.
- **`docs/CONTEXTO_FROTA_MAIS.md`:** fonte de verdade sobre regras de negócio, roadmap (seção 6.6) e pendências (Anexo A). Leia as seções relevantes antes de tocar em um domínio. Se divergir do código, reporte; não ajuste um para coincidir com o outro.
- **`contexto-local/`:** privado, ignorado pelo Git e presente só em alguns clones. Nunca copie o conteúdo para arquivos versionados. O repositório é **público**.

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
| Subir a API na 8080 | `./run-dev-frotamais.sh` | `.\mvnw.cmd spring-boot:run` |
| Testar uma classe | `./mvnw test -Dtest=SegurancaCaminhaoTest` | `.\mvnw.cmd test -Dtest=SegurancaCaminhaoTest` |
| Testar um método | `./mvnw test -Dtest=SegurancaCaminhaoTest#deveBloquearListagemSemToken` | `.\mvnw.cmd test -Dtest=SegurancaCaminhaoTest#deveBloquearListagemSemToken` |
| Toda a suíte | `./mvnw test` | `.\mvnw.cmd test` |

**Scripts `.sh`** rodam apenas em ambiente compatível com Bash, como o Codespaces, Git Bash ou WSL. Não funcionam no PowerShell nem no `cmd`. `run-dev-frotamais.sh` carrega `.frotamais-jwt.env` e exige `JWT_SECRET`; sem o script, a variável precisa estar definida na sessão antes de subir a API.

Não há linter configurado.

### Testes e banco de dados

Todos os testes atuais sobem o contexto completo contra o banco exclusivo `frotamais_test`, nunca contra `frotamais`. Precisam do Postgres ligado e do banco criado (`CREATE DATABASE frotamais_test`). `src/test/resources/config/application.properties` fixa a URL, e `GuardaBancoDeTeste` interrompe a execução se a conexão efetiva for outra.

- **Não enfraqueça esse isolamento:** não remova o guarda nem aponte os testes para `frotamais`.
- **Não execute sem autorização explícita** testes que gravam dados permanentes no banco de desenvolvimento.
- **Testes novos que gravam dados** devem usar `@Transactional`, para desfazer os registros ao final.

### `pom.xml`

O arquivo tem uma linha em branco antes de `<?xml`, o que é inválido em XML. O Maven tolera no máximo 1 byte nessa posição: `\n` passa e `\r\n` falha com `Non-parseable POM`. A causa foi comprovada (seção 6.3 do documento de contexto), e a correção recomendada, remover esse byte inicial, ainda não foi aplicada.

## Arquitetura

Monólito Spring Boot 4.1.1 na raiz do repositório, com pacotes por funcionalidade em `br.com.frotamais`. Cada funcionalidade segue Controller → Service → Repository, com entidade JPA separada de DTOs `record` (`XRequest`/`XResponse`) e nomes de domínio em português. Use `caminhao/` como molde.

**Fluxo de autenticação (atravessa `seguranca/` e `auth/`):**
1. `AuthService` valida a senha com BCrypt e `TokenService` emite um JWT HS256 com `sub`, `empresaId` e `perfil`.
2. A cada requisição, o `conversor()` de `SecurityConfig` recarrega o usuário do banco, exige que esteja ativo e compara o `empresaId` do token com o do banco.
3. As authorities `ROLE_*` vêm **do banco**, então desativar um usuário ou mudar o perfil vale na hora. A claim `perfil` não é usada para autorizar.

**Isolamento por empresa:** os serviços obtêm o `empresaId` de `UsuarioLogado`, nunca do JSON, e consultam por métodos como `findByIdAndEmpresaId`. Recurso de outra empresa responde `404`. Isso só está garantido em `CaminhaoRepository`. `JpaRepository` herda `findById`, `findAll` e `deleteById` sem filtro, então o compilador não protege você. Toda funcionalidade nova precisa de isolamento verificável em todas as consultas e operações. A entidade pode ter vínculo direto com a empresa ou pertencer a outra entidade que tenha esse vínculo, desde que a segurança seja garantida e comprovada por teste de isolamento com tokens reais.

**Erros seguem dois caminhos:** `ApiExceptionHandler` (em `auth/`, mas global) trata validação e `DataIntegrityViolationException`. `ResponseStatusException` e o restante passam pelo `/error` do Spring, com outro formato, e a mensagem provavelmente não chega ao cliente, porque `include-message` é `never` por padrão.

**Schema:** `ddl-auto=update`, sem migrations. Remover uma restrição da entidade não a remove do banco.

**Spring Boot 4:** Jackson é 3.x (`tools.jackson.databind`), `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure` e `include-message` é `spring.web.error.*`. Não troque imports por memória do Boot 3.

**Testes:** `jwt()` do `spring-security-test` contorna o `conversor()`. Um teste com ele prova o `@PreAuthorize`, não o fluxo real.

**Lacunas conhecidas:** não existe criação de MOTORISTA nem vínculo motorista–caminhão, e o e-mail ainda é único global, embora a regra confirmada permita o mesmo e-mail em empresas diferentes. Não escreva código novo que dependa dessa unicidade.

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
