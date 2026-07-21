# TODO

## Servidor

- [ ] Corrigir o logging do servidor para incluir a identidade do utilizador (email/role) em cada request
- [ ] Adicionar a claim de email ao JWT para facilitar o logging e rastreabilidade
- [ ] Investigar que valor usar para `maxFrameSize` na configuração das WebSockets
- [ ] (NEW) Tornar prazo de expiração dos tokens de joins independentes da timezone
- [ ] (NEW) Fazer cleanup automático de tokens expirados

## Serviço

- [ ] Analisar e melhorar o error handling do Daemon
- [ ] Investigar como tornar os monitores mais robustos
- [ ] (NEW) Investigar a performance no momento da inicialização do ProcessBlocker
- [ ] Corrigir o problema em que o serviço não interceta nem termina processos proibidos
- [ ] Adicionar verificação de que o daemon está ligado (CONNECTED) antes de considerar o estudante pronto na sessão
- [ ] (NEW) Tornar implementação de monitorização de processos independente da plataforma
- [ ] (NEW) Analisar os StartAsync e mudar o tipo de retorno da Task

## Consola do Professor

- [ ] Adicionar filtro/arquivo para ocultar exames e sessões terminadas
- [ ] Adicionar opção de eliminar ou arquivar exames
- [ ] Criar um campo para a sala no formulário de criação de exame em vez de estar explicito no título
- [ ] Usar reducer hook no componente Dashboard e Login que tem bastantes estados
- [ ] Sinalizar e guardar alunos que tiveram atualizações nos eventos e não foram visto pelo professor
- [ ] (NEW) Criar visual para mostrar que componentes estão offline na maquina do aluno

## Documentação

- [ ] Refazer os diagramas com menos ligações e melhor layout
- [ ] Não focar tanto em código em relação às abstrações do Sentinela e começar por abordar de uma forma geral as abstrações Monitor, Mitigator e as restantes interfaces de plataforma.

## Dúvidas
- Para comunicação bidirecional com pipes, devemos usar dois one-way named pipes ou um two-way named pipe?

## Concluído

- [X] Integrar o daemon com WebSockets [12/5]
- [X] Resolver vulnerabilidades do ClipboardBlocker [16/5]
- [X] Evitar o logging repetido e excessivo especialmente no NetworkMonitor e ProcessMonitor [17/5]
- [X] Desenvolver o esqueleto da consola do professor
- [X] Organizar estrutura da implementação do ClipboardBlocker a nível da plataforma
- [X] Redesenhar o student card para mostrar as infrações inline com scroll em vez de tooltip flutuante (resolve o bug de hover) [27/5]
- [X] Adicionar a claim de email ao JWT para facilitar o logging e rastreabilidade [29/5] 
- [X] Alterar o campo supervisor_id para um array de IDs e suportar vários supervisor para uma sessão [30/5] 
- [X] Permitir que contas de professores acedam a sessões ativas usando os códigos [30/5] 
- [X] Adicionar verificação de que o daemon está ligado (CONNECTED) antes de considerar o estudante pronto na sessão [31/5]
- [X] Indicador visual de timeout do daemon [31/5]
- [X] Adicionar flow para professores acederem a sessões através do código (Polling) [31/5]
- [X] (NEW) Fazer pdf info
- [X] (NEW) Fazer Cartaz
- [X] (NEW) Fazer enquadramento/requisitos funcionais e arquitetura da solução (Relatório final)
- [X] Suportar vários supervisor para uma sessão [1/6]
- [X] Reestruturar a secção Introdução e começar com a secção do Enquadramento ou Requisitos Funcionais [5/6]
- [X] Permitir que contas de professores acedam a sessões ativas usando os códigos
- [X] Redirecionar para login quando utilizador tem token expirado [13/6]
- [X] (NEW) Adicionar flow para professores acederem a sessões através do código (SSE) [12/6]
- [X] (NEW) Começar o capítulo de Arquitetura da solução [18/6]
- [X] (NEW) Procurar solução para a autenticação do professor [20/6]
- [X] (NEW) Substituir script "join-session.ps1" com sistema de autenticação [22/6]




