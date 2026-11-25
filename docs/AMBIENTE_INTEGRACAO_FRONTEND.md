# Ambiente de integração com o front-end

Guia para quem desenvolve o front-end (Angular) e precisa chamar a API real do
Frota+. Os contratos HTTP estão em [`API.md`](API.md); uma coleção pronta de
requisições está em [`frotamais-api.http`](frotamais-api.http).

> Nenhuma senha, token ou segredo aparece aqui. Cada ambiente gera os seus.

## 1. Resumo

| Item | Valor |
|---|---|
| Java | **21** (o projeto não compila com 17) |
| Banco | PostgreSQL 16 em `localhost:5432` (já vem no devcontainer) |
| Banco de integração | `frotamais_integracao`: exclusivo, montado só pelas migrations, só com dados fictícios |
| Perfil Spring | `dev` |
| URL local | `http://localhost:8080` |
| Prefixo das rotas | `/api` |
| Health check | Não há (sem Actuator). Use `GET /api/caminhoes` sem token: a resposta esperada é `401` com JSON |
| Autenticação | `Authorization: Bearer <token>` (JWT obtido no login, no registro ou na ativação do convite) |
| CORS | **Não configurado.** Use o proxy do Angular (seção 6) |

Bancos que **não** devem ser usados para integração: `frotamais` (banco de
desenvolvimento persistente, ainda sem o Flyway adotado; a API se recusa a subir
nele) e `frotamais_test_flyway` (exclusivo da suíte de testes).

## 2. Caminho recomendado: um Codespace com o back-end e o front-end

O devcontainer deste repositório já traz Java 21, PostgreSQL 16, Node LTS, o
Angular CLI e o encaminhamento das portas `8080` (API) e `4200` (Angular).
O caminho mais simples e seguro é rodar **os dois no mesmo Codespace**:

```
navegador ──► 4200 encaminhada (privada, sua conta GitHub)
                └─ ng serve + proxy /api ──► http://localhost:8080 (API)
                                                └─ localhost:5432 (frotamais_integracao)
```

Assim nenhuma porta precisa ficar pública e não é preciso CORS: o navegador
só fala com o servidor do Angular, que repassa `/api` para a API dentro do
mesmo Codespace.

### Passo a passo (uma vez por Codespace)

1. Crie um Codespace a partir deste repositório, na branch que contém este
   documento e o `run-integracao-frotamais.sh` (`Code` → `Codespaces` →
   `Create codespace on <branch>`), e espere o devcontainer terminar.
2. No terminal, na raiz do back-end, gere a sua chave JWT (fica só no seu
   Codespace; o arquivo é ignorado pelo Git):
   ```bash
   [ -e .frotamais-jwt.env ] || printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 32)" > .frotamais-jwt.env
   chmod 600 .frotamais-jwt.env
   ```
3. Crie o banco de integração, vazio:
   ```bash
   psql -h localhost -U postgres -c "CREATE DATABASE frotamais_integracao"
   ```
   Se o `psql` pedir senha, use a senha local do PostgreSQL do devcontainer
   (definida em `.devcontainer/docker-compose.yml`; vale só dentro do
   Codespace).
4. Clone o front-end ao lado do back-end:
   ```bash
   cd /workspaces && git clone <URL do repositório do front-end>
   ```

## 3. Como iniciar o back-end

Em um terminal dedicado, na raiz do back-end:

```bash
./run-integracao-frotamais.sh
```

O script:

- força `DB_URL` para `frotamais_integracao` (ou o nome em
  `FROTAMAIS_INTEGRACAO_DB`) e **recusa** `frotamais` ou qualquer banco com
  `test` no nome;
- confere se o banco existe e se a porta `8080` está livre (uma instância só);
- ativa o perfil `dev` e chama o `run-dev-frotamais.sh`, que carrega a chave
  de `.frotamais-jwt.env` e para com erro claro se ela faltar.

Na primeira subida o Flyway cria as tabelas (V1 a V11). A subida terminou
quando o log mostrar:

```
Successfully applied 11 migrations ... now at version v11   (só na primeira vez)
Tomcat started on port 8080 (http)
Started BackendApplication in ... seconds
```

Se faltar a chave: `ERRO: JWT_SECRET não definido.` Chave curta ou inválida:
a aplicação para dizendo que `JWT_SECRET deve representar pelo menos 32 bytes`.

### Variáveis de ambiente

| Variável | Obrigatória | Observação |
|---|---|---|
| `JWT_SECRET` | sim | Base64 de pelo menos 32 bytes, em `.frotamais-jwt.env` |
| `SPRING_PROFILES_ACTIVE` | o script define `dev` | Sem perfil, a configuração é a de produção e exige `DB_URL`, `DB_USER` e `DB_PASSWORD` |
| `FROTAMAIS_INTEGRACAO_DB` | não | Nome do banco de integração (padrão `frotamais_integracao`) |
| `JWT_HORAS` | não | Validade do token (padrão 8 h, entre 1 e 24) |
| `CONVITE_URL_ATIVACAO` | para convites | Página de ativação do front-end; o link do convite é `<URL>?token=...`. Sem ela, convites respondem `503` |
| `EMAIL_CAPTURA_LOCAL` | não | `true` liga a captura local de e-mails (só perfil `dev`; seção 9) |
| `EMAIL_CAPTURA_DIR` | não | Diretório da captura (padrão `contexto-local/emails-dev`; precisa estar dentro de `contexto-local/`) |
| SMTP (`SPRING_MAIL_*`) e `EMAIL_REMETENTE` | não | Envio real de e-mail (seção 9). Sem SMTP nem captura, convites respondem `503` |

O perfil `dev` só vale quando ativado explicitamente; a configuração base não
tem credenciais padrão e não sobe sem elas.

### Como interromper

`Ctrl+C` no terminal da API. Se ela estiver em segundo plano:

```bash
pkill -f 'spring-boot:run'     # ou: kill <PID do java que escuta a 8080>
ss -ltnp | grep ':8080' || echo "8080 livre"
```

## 4. Como testar se a API está funcionando

```bash
curl -i http://localhost:8080/api/caminhoes
```

Esperado: `401` com
`{"status":401,"erro":"Não autenticado","detalhes":[...]}`. Isso prova que a
API está no ar e que a segurança está ativa. Para um teste completo, abra
[`frotamais-api.http`](frotamais-api.http) no VS Code (extensão REST Client, já
instalada pelo devcontainer) e rode, na ordem: registro, login, cadastro de
caminhão, abertura de viagem, lançamentos, finalização e indicadores.

## 5. Autenticação e envio do JWT

1. `POST /api/auth/registro` (cria empresa + DONO) ou `POST /api/auth/login`
   devolvem `{token, nome, perfil}`.
2. Envie o token em toda rota protegida:
   `Authorization: Bearer <token>`.
3. `401`: sem token, token inválido ou expirado, usuário inativo. Limpe a
   sessão e volte ao login. `403`: perfil sem permissão (ex.: MOTORISTA em
   rota só do DONO).
4. O perfil vem do banco a cada requisição: desativar um usuário vale na hora.
   O front-end nunca envia `empresaId`.

Use **dados fictícios** (e-mails em `@exemplo.test`, por exemplo). O e-mail é
único em todo o sistema: repetir dá `409`.

## 6. Proxy do Angular (desenvolvimento)

Crie `proxy.conf.json` na raiz do projeto Angular:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

E inicie o servidor de desenvolvimento com ele:

```bash
ng serve --proxy-config proxy.conf.json
```

(ou `"proxyConfig": "proxy.conf.json"` em `angular.json`, em
`serve.options`). No código, a URL base da API fica em configuração de
ambiente e, em desenvolvimento, é relativa: `/api/...`. Abra o front-end pela
URL encaminhada da porta `4200` (aba **Ports** do VS Code).

### CORS

O back-end **não** tem CORS configurado. Conferido em 23/11/2026:

- requisição repassada pelo proxy (mesmo com cabeçalho `Origin`): `200`,
  funciona;
- preflight `OPTIONS` direto do navegador, de outra origem: `401`, o navegador
  bloqueia.

Com o proxy, **nenhuma origem precisa ser liberada**. Chamar a API
diretamente do navegador, de outra origem, exige configurar CORS no back-end
com a origem exata do front-end (por exemplo
`https://<codespace-do-front>-4200.app.github.dev`), apenas os métodos usados
(`GET`, `POST`, `PUT`, `DELETE`) e os cabeçalhos `Authorization` e
`Content-Type`, nunca `*`. Isso é uma mudança no back-end e precisa de
aprovação do responsável.

## 7. Acesso a partir de outro Codespace ou máquina

No Codespaces, a API fica em
`https://<CODESPACE_NAME>-8080.app.github.dev`, com visibilidade **privada**
por padrão. Conferido em 23/11/2026:

- sem login no GitHub: `302` para a tela de entrada do GitHub;
- com a credencial do **dono** do Codespace: a requisição chega à API.

| Visibilidade | Quem acessa |
|---|---|
| Privada (padrão) | Só o dono do Codespace, autenticado no GitHub |
| Organização | Membros da organização (só existe em Codespaces de organização; este repositório é de conta pessoal) |
| Pública | Qualquer pessoa com a URL, sem login no GitHub. A API continua exigindo JWT, mas registro, login e ativação ficam abertos à internet |

Portanto, **outra pessoa não acessa a API do Codespace de outra conta** sem
que a porta fique pública. Tornar a porta `8080` pública exige autorização do
responsável pelo back-end, porque:

- expõe na internet `POST /api/auth/registro`, `/login` e
  `/api/convites/ativacao`, que **não têm rate limit**;
- qualquer pessoa com a URL pode criar empresas no banco de integração;
- a URL dura enquanto o Codespace estiver ligado e a porta pública.

Se for autorizado: aba **Ports** → porta 8080 → `Port Visibility` → `Public`;
o proxy do Angular passa a usar
`"target": "https://<CODESPACE_NAME>-8080.app.github.dev"` com
`"secure": true` e `"changeOrigin": true`. Para encerrar o acesso, volte a
visibilidade para `Private` ou pare o Codespace. O banco usado deve continuar
sendo o `frotamais_integracao`, só com dados fictícios. A porta `5432`
(PostgreSQL) **sempre** fica privada.

Por isso o caminho recomendado é o da seção 2: cada desenvolvedor roda a API
no próprio Codespace.

## 8. Endpoints disponíveis (41)

Detalhes de corpo, regras e códigos em [`API.md`](API.md).

| Grupo | Rotas | Perfis |
|---|---|---|
| Autenticação | `POST /api/auth/registro`, `POST /api/auth/login` (públicas), `POST /api/auth/senha` | todos |
| Convite | `POST /api/convites/ativacao` (pública) | — |
| Caminhões | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` em `/api/caminhoes` | escrita só DONO; MOTORISTA vê os vinculados |
| Motoristas | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `PUT /{id}/caminhoes`, `POST /{id}/convite`, `DELETE /{id}/convite` em `/api/motoristas` | só DONO |
| Viagens | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `POST /{id}/finalizar` em `/api/viagens` | DONO e MOTORISTA (vinculado) |
| Viagens (só DONO) | `POST /{id}/cancelar`, `POST /historicas`, `GET /{id}/alteracoes`, `GET /{id}/fotografias-anteriores`, `GET /indicadores`, `GET /indicadores/por-caminhao` | só DONO |
| Lançamentos | `POST`, `PUT /{id}`, `DELETE /{id}` em `/api/viagens/{viagemId}/abastecimentos` (diesel e Arla), `/pedagios` e `/despesas-extras` | DONO e MOTORISTA (vinculado) |
| Manutenções | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` em `/api/manutencoes` | só DONO |

Convenções: datas `AAAA-MM-DD`, horas `HH:MM`, dinheiro com até 2 casas,
`PUT` é substituição integral, valores calculados nulos significam "não
calculável" (nunca zero), erros sempre em `{"status","erro","detalhes"}`.
Recurso de outra empresa responde `404`.

## 9. E-mail, convites e teste do perfil MOTORISTA

O MOTORISTA só existe por convite: o DONO cadastra (`POST /api/motoristas`),
o back-end gera um token de uso único (guardado só como hash, válido por 24 h)
e envia o link `<CONVITE_URL_ATIVACAO>?token=...`; a página de ativação do
front-end chama `POST /api/convites/ativacao` com `token` e `senha`, recebe
`{token, nome, perfil: "MOTORISTA"}` e a partir daí o motorista entra pelo
login normal. Não há atalho: não cadastre motoristas direto no banco.

Há três modos de envio, escolhidos na subida:

| Modo | Quando | Efeito |
|---|---|---|
| Sem e-mail | padrão | Cadastrar motorista e reenviar convite respondem `503`; nada é gravado |
| Captura local (desenvolvimento) | perfil `dev` + `EMAIL_CAPTURA_LOCAL=true` | Nenhum e-mail sai da máquina; cada mensagem vira um arquivo em `contexto-local/emails-dev/` |
| SMTP real | `SPRING_MAIL_*` + `EMAIL_REMETENTE` | Envio real (exige um provedor escolhido e configurado pelo responsável) |

### Captura local (desenvolvimento)

Para testar o fluxo de motorista sem SMTP, o responsável pelo back-end sobe a
API assim (troque a URL pela rota real da página de ativação do front-end):

```bash
EMAIL_CAPTURA_LOCAL=true CONVITE_URL_ATIVACAO=http://localhost:4200/ativar-conta ./run-integracao-frotamais.sh
```

O log da subida mostra `Captura local de e-mails ATIVA (perfil dev)`.
Proteções:

- funciona **só** com o perfil `dev`; com a chave ligada em outro perfil, ou
  junto com SMTP, a API **se recusa a subir**;
- grava só dentro de uma pasta `contexto-local/` (ignorada pelo Git), com
  diretório `700` e arquivos `600`;
- aceita só destinatários de domínios reservados para teste (`.test`,
  `.example`, `.invalid`, `.localhost`, `example.com`, `example.net`,
  `example.org`). Outro domínio responde `503` e nada é gravado: use e-mails
  como `motorista1@exemplo.test`;
- não há endpoint para ler convites, a resposta HTTP nunca traz o token e o
  log registra só o nome do arquivo (sem destinatário nem link).

Quem tem acesso ao terminal do Codespace lê o convite mais recente com:

```bash
ls -t contexto-local/emails-dev/ | head -1                       # arquivo mais recente
grep -l 'Para: motorista1@exemplo.test' contexto-local/emails-dev/*.txt
```

O link dentro do arquivo é a credencial de ativação: não cole em chats,
issues ou capturas de tela. Os arquivos podem ser apagados quando não forem
mais úteis.

### Passo a passo para o front-end testar o MOTORISTA

1. Entre como DONO e cadastre um caminhão (vinculado) e outro (não vinculado).
2. Pela tela de motoristas, convide `motorista1@exemplo.test` com o caminhão
   vinculado: `201`, `situacao: "CONVITE_PENDENTE"`.
3. Peça o link a quem roda o back-end (ou leia o arquivo, se for você) e abra
   na página de ativação; ela chama `POST /api/convites/ativacao`.
4. A ativação devolve `perfil: "MOTORISTA"`; reutilizar o mesmo link dá `400`.
5. Faça login como o motorista e confira:
   - `GET /api/caminhoes` lista só o caminhão vinculado; o outro dá `404`;
   - abrir viagem e lançar abastecimento, pedágio e despesa no caminhão
     vinculado funciona; `rentabilidade` vem `null`;
   - indicadores, motoristas, manutenções, auditoria, cadastro de caminhão,
     cancelamento e viagem histórica dão `403`.

Conferido pela API real em 23/11/2026, com dados fictícios, exatamente nessa
sequência.

### SMTP real

Variáveis (valores fornecidos pelo responsável; nunca no Git):
`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
`SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`,
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `EMAIL_REMETENTE` e
`CONVITE_URL_ATIVACAO`. A escolha do provedor (e de eventual custo ou
cadastro) é do responsável pelo back-end. Não use SMTP e captura juntos.

## 10. Mensagens de erro

`detalhes` vem sempre em português, no formato `campo: mensagem` para erros
de validação (por exemplo `modelo: não pode ficar em branco`,
`eixos: deve ser maior ou igual a 1`, `abastecimentos[0].litros: é
obrigatório`), independentemente do idioma do navegador ou do servidor. Mostre
o texto ao usuário; para destacar o campo, use o prefixo antes de `:` só como
dica visual.

## 11. Limitações conhecidas

- **Sem health check** dedicado (sem Actuator).
- **Sem CORS**: use o proxy (seção 6).
- **E-mail real não configurado**: fora da captura local (seção 9), convites
  respondem `503`. A rota da página de ativação do front-end ainda precisa ser
  informada ao back-end para compor o link (`CONVITE_URL_ATIVACAO`).
- **Validação antes da permissão**: numa rota proibida ao perfil, um corpo
  inválido responde `400` antes do `403` (ex.: MOTORISTA enviando `{}` para
  `POST /api/viagens/historicas`). Com corpo válido, `403`. Nada é gravado.
- **Sem rate limit** em login, registro e ativação.
- **Sem paginação**; sem refresh token (sessão de 8 h por padrão); sem
  endpoint `/me`.
- Datas de viagem não têm hora: duas viagens do mesmo caminhão não começam no
  mesmo dia, e datas no futuro dão `400`.

## 12. O que exige autorização do responsável pelo back-end

- Tornar qualquer porta pública ou publicar a API.
- Configurar CORS ou alterar qualquer contrato, regra de negócio ou permissão.
- Configurar SMTP, ligar a captura local de e-mails e definir a URL de ativação.
- Qualquer operação no banco `frotamais` (adoção do Flyway, baseline) ou
  operação destrutiva em qualquer banco (`DROP`, `TRUNCATE`, `DELETE`).
- Commit, push e merge neste repositório.
