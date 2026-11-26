# Frota+: e-mail (SMTP), convites de motorista e proteções do login

## 1. Fluxo do convite

1. O DONO cadastra o motorista: `POST /api/motoristas` (`nome`, `email`, `caminhaoIds`).
2. A API cria a conta sem senha (`CONVITE_PENDENTE`) e um convite com token
   aleatório de 256 bits. O banco guarda só o SHA-256 do token.
3. O e-mail sai **dentro da transação**. Se o envio falhar, ou se o SMTP não
   estiver configurado, a resposta é `503` e nada é gravado. A resposta HTTP
   nunca contém o token nem o link.
4. O motorista abre `CONVITE_URL_ATIVACAO?token=...` (tela do front-end), e a
   tela chama `POST /api/convites/ativacao` com `token` e `senha`.
5. A ativação define a senha, ativa a conta, devolve `{token, nome, perfil}`
   e invalida o convite. O token é de uso único e vale `CONVITE_VALIDADE_HORAS`
   (padrão 24 h). Reenviar (`POST /api/motoristas/{id}/convite`) invalida o
   anterior; cancelar é `DELETE` na mesma rota.
6. O motorista entra pelo `POST /api/auth/login` e só vê os caminhões vinculados
   e as viagens deles, sem custos, indicadores, manutenção nem auditoria.

## 2. Variáveis de ambiente

Nenhuma delas vai para arquivo versionado. Cadastre-as no painel de segredos da hospedagem.

| Variável | Exemplo (fictício) | Observação |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp.provedor.exemplo` | Sem ele, convites respondem `503` |
| `SPRING_MAIL_PORT` | `587` | `587` com STARTTLS (recomendado) |
| `SPRING_MAIL_USERNAME` | `usuario-smtp` | Fornecido pelo provedor |
| `SPRING_MAIL_PASSWORD` | (segredo) | Chave ou senha SMTP do provedor, nunca a senha pessoal da conta |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | `true` | |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | `true` | Use também `..._STARTTLS_REQUIRED=true` para não aceitar conexão sem TLS |
| `EMAIL_REMETENTE` | `nao-responda@dominio.exemplo` | Precisa estar **verificado** no provedor |
| `CONVITE_URL_ATIVACAO` | `https://app.exemplo/ativar-conta` | Tela de ativação do front-end (https em produção) |
| `CONVITE_VALIDADE_HORAS` | `24` | De 1 a 720 |
| `SMTP_TIMEOUT_MS` | `10000` | Timeout de conexão, leitura e escrita (padrão 10 s). Estourou: `503`, nada gravado |

Porta 465 (SSL direto) também funciona: `SPRING_MAIL_PORT=465` e
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true`, sem STARTTLS. Algumas
hospedagens bloqueiam a porta 25; não a use.

**Falhas:** autenticação, timeout ou recusa do provedor resultam em `503`
("Não foi possível enviar o e-mail agora. Nada foi gravado"). O log registra só
o tipo da exceção, nunca o destinatário, o texto ou o link.

## 3. Provedores analisados (pesquisa em 25/11/2026)

Os números vêm das páginas oficiais citadas, que mudam com frequência.
**Confira os limites e o host SMTP no painel do provedor antes de configurar.**
Nenhuma conta foi criada.

| Provedor | Gratuito | Remetente | Observações |
|---|---|---|---|
| **Brevo** | 300 e-mails/dia, sem prazo ([brevo.com](https://www.brevo.com/en/free-smtp-server/), [blog](https://www.brevo.com/blog/free-smtp-servers/)) | Verificação do endereço remetente; domínio próprio é recomendado | SMTP incluso. Host habitual: `smtp-relay.brevo.com:587` (**confirmar no painel**: a pesquisa não achou confirmação oficial) |
| **Resend** | 100/dia e 3.000/mês ([docs](https://resend.com/docs/knowledge-base/account-quotas-and-limits), [limites](https://resend.com/docs/knowledge-base/resend-sending-limits)) | **Exige domínio próprio** verificado por DNS (DKIM/SPF) ([changelog](https://resend.com/changelog/three-domains-on-the-free-tier)) | Boa entregabilidade; sem domínio só serve para testes. A verificação de DNS pode levar horas |
| **Mailjet** | 6.000/mês, 200/dia, sem cartão ([preços](https://www.mailjet.com/pricing/), [SMTP](https://www.mailjet.com/products/email-api/smtp-relay)) | Verificação do remetente | Coloca a marca Mailjet nos e-mails do plano gratuito |
| **Gmail (senha de app)** | Limite diário da conta ([Google](https://support.google.com/a/answer/176600)) | A própria conta Gmail | `smtp.gmail.com:587`. Exige verificação em 2 etapas e senha de app; trocar a senha da conta revoga a senha de app. Remetente pessoal, não de domínio |
| **Twilio SendGrid** | Plano grátis encerrado em 2025; só teste de 60 dias, 100/dia ([changelog](https://www.twilio.com/en-us/changelog/sendgrid-free-plan), [preços](https://www.twilio.com/en-us/pricing/email-api)) | Domínio ou remetente verificado | **Não recomendado**: o envio para ao fim do teste |

### Recomendação

- **Apresentação acadêmica, sem domínio próprio:** **Brevo**. É gratuito sem
  prazo, aceita remetente verificado por e-mail e tem 300/dia, mais do que o
  necessário. Risco: remetente `@gmail.com`/`@hotmail.com` enviado por terceiros
  pode cair no spam (DMARC). Teste antes da apresentação e peça para conferir a
  caixa de spam.
- **Alternativa mais rápida:** Gmail com senha de app, se o responsável já
  tiver uma conta com verificação em 2 etapas. Só para demonstração.
- **Produção real com domínio:** Resend ou Brevo com domínio verificado (SPF e DKIM).

### O que o responsável precisa fornecer

1. Conta criada no provedor escolhido e remetente (ou domínio) verificado.
2. `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME` e
   `SPRING_MAIL_PASSWORD` (a chave SMTP gerada no painel).
3. `EMAIL_REMETENTE`, exatamente o endereço verificado.
4. `CONVITE_URL_ATIVACAO`, a URL https da tela de ativação do front-end.

## 4. Testar o fluxo de MOTORISTA sem SMTP (desenvolvimento)

A **captura local** grava cada e-mail num arquivo, em vez de enviar. Proteções:

- só funciona com o perfil `dev` **e** `EMAIL_CAPTURA_LOCAL=true`;
- em `prod`, a variável é ignorada (só o `application-dev.properties` a mapeia),
  e forçar a propriedade fora do `dev` impede a API de subir;
- junto com SMTP configurado, a API também não sobe;
- grava só dentro de `contexto-local/` (ignorado pelo Git), com diretório `700`
  e arquivos `600`;
- só aceita destinatários de domínios reservados (`.test`, `example.com`...).
  Qualquer outro recebe `503`, para que dados de pessoas reais nunca sejam gravados;
- o log registra só o nome do arquivo, nunca o link.

Passo a passo (Codespaces, banco isolado montado pelas migrations):

```bash
psql -h localhost -U postgres -c "CREATE DATABASE frotamais_ensaio_motorista"   # só na primeira vez
FROTAMAIS_INTEGRACAO_DB=frotamais_ensaio_motorista EMAIL_CAPTURA_LOCAL=true \
CONVITE_URL_ATIVACAO=http://localhost:4200/ativar-conta ./run-integracao-frotamais.sh
```

1. Registre um DONO (`POST /api/auth/registro`, e-mail `@exemplo.test`).
2. Cadastre um caminhão.
3. Convide o motorista (`POST /api/motoristas`) com e-mail fictício `@exemplo.test`.
4. Abra o arquivo mais recente de `contexto-local/emails-dev/`. O link está lá.
   Não copie o link para chats, issues ou commits.
5. Ative: `POST /api/convites/ativacao` com o `token` do link e a senha.
6. Faça login como MOTORISTA e teste as permissões.

Resultado em 25/11/2026: 64 verificações OK (convite, captura, ativação, JWT
e matriz de permissões, isolamento e desativação), sem link ou token no log.

## 5. Proteções do login e dos endpoints públicos

### Limite de tentativas (`429`)

| Endpoint | Chave | Padrão (`app.tentativas.*`) |
|---|---|---|
| `POST /api/auth/login` | IP | 30 por 5 min |
| `POST /api/auth/login` (falhas) | e-mail normalizado | 10 falhas por 15 min; zera no login certo |
| `POST /api/auth/registro` | IP | 10 por 60 min |
| `POST /api/convites/ativacao` | IP | 20 por 15 min |

- A resposta é `429` no formato padrão, com `Retry-After` em segundos (exposto
  no CORS). O bloqueio nunca é permanente: termina com a janela.
- O limite roda antes da validação do corpo e do serviço: requisição bloqueada
  não grava nada.
- O bloqueio por e-mail vale para contas existentes e inexistentes, com a
  mesma resposta (não revela cadastro). Bloqueia inclusive a senha certa até
  a janela acabar. É o preço de impedir a força bruta, e é temporário.
- O perfil `dev` usa limites por IP maiores (pelo Codespaces todos chegam com
  o mesmo IP). No `prod`, desligar o limite impede a API de subir.

**IP real atrás de proxy:** o código só usa `request.getRemoteAddr()`. No
`prod`, `server.forward-headers-strategy=native` faz o Tomcat aceitar o
`X-Forwarded-For` **somente** de proxies confiáveis (redes privadas, por
padrão) e escolher o primeiro IP não confiável da direita para a esquerda.
Assim, o que o cliente escreve no cabeçalho não substitui o IP real (testado
em `LimiteTentativasProxyTest` e no ensaio do jar). Ao escolher a hospedagem:

- confira se o proxy dela chega de rede privada. Se não chegar, informe a
  faixa em `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (regex);
- sem proxy na frente: `FORWARD_HEADERS_STRATEGY=none`;
- com proxy público não declarado, todos os clientes pareceriam o IP do proxy
  e dividiriam o mesmo limite. É o sintoma a observar no primeiro deploy.

**Limitações (em memória):** os contadores ficam na memória da instância (no
máximo 20.000 chaves por regra, descartando as menos usadas), zeram ao
reiniciar e não são compartilhados entre instâncias. Para a primeira
publicação (uma instância), basta. Com várias instâncias, ou contra ataques
distribuídos de muitos IPs, use também o rate limit ou WAF da hospedagem.

### Enumeração de e-mail no login

E-mail inexistente, senha errada, conta inativa e convite não aceito recebem
exatamente o mesmo `401` (`E-mail ou senha inválidos.`) e fazem o mesmo
trabalho: uma verificação BCrypt. Quando não há senha a conferir, a
verificação é feita contra um hash fictício gerado ao subir, com o mesmo
custo. Não há atraso artificial. Testes: `LoginEnumeracaoTest` (uma
verificação em cada caso, respostas idênticas e tempos comparáveis).

O registro continua respondendo `409 E-mail já cadastrado` (contrato atual,
e o e-mail é único no sistema). Ali, a proteção é o limite por IP.
