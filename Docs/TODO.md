# TODO

## Servidor

- [ ] Investigar que valor usar para `maxFrameSize` na configuração das WebSockets

## Daemon

- [ ] Analisar e melhorar o error handling do Daemon
- [ ] Investigar como tornar os monitores mais robustos
- [ ] Investigar a performance do daemon como serviço do Windows principalmente ao parar
- [ ] Corrigir o problema em que o serviço não interceta nem termina processos proibidos

## Consola do Professor

- [ ] Adicionar filtro/arquivo para ocultar exames e sessões terminadas
- [ ] Adicionar opção de eliminar ou arquivar exames
- [ ] Criar um campo para a sala no formulário de criação de exame em vez de estar explicito no título
- [ ] Usar reducer hook no componente Dashboard e Login que tem bastantes estados
- [ ] (NEW) Adicionar integração de OAuth 2.0
- [ ] (NEW) Adicionar flow para professores acederem a sessões através do código (SSE)

## Documentação

- [ ] Reestruturar a secção Introdução e começar com a secção do Enquadramento ou Requisitos Funcionais **(3º prioridade)**
- [ ] Refazer os diagramas com menos ligações e melhor layout **(3º prioridade)**
- [ ] Analisar o código do repositório
- [ ] (NEW) Fazer pdf info
- [ ] (NEW) Fazer Cartaz
- [ ] (NEW) Fazer enquadramento/requisitos funcionais e arquitetura da solução (Relatório final)

## Dúvidas


## Concluído

- [X] Integrar o daemon com WebSockets [12/5]
- [X] Resolver vulnerabilidades do ClipboardBlocker [16/5]
- [X] Evitar o logging repetido e excessivo especialmente no NetworkMonitor e ProcessMonitor [17/5]
- [X] Desenvolver o esqueleto da consola do professor
- [X] Redesenhar o student card para mostrar as infrações inline com scroll em vez de tooltip flutuante (resolve o bug de hover) [27/5]
- [X] (NEW) Adicionar a claim de email ao JWT para facilitar o logging e rastreabilidade [29/5] 
- [X] (NEW) Alterar o campo supervisor_id para um array de IDs e suportar vários supervisor para uma sessão [30/5] 
- [X] (NEW) Permitir que contas de professores acedam a sessões ativas usando os códigos [30/5] 
- [X] (NEW) Adicionar verificação de que o daemon está ligado (CONNECTED) antes de considerar o estudante pronto na sessão [31/5]
- [X] (NEW) Indicador visual de timeout do daemon [31/5]
- [X] (NEW) Adicionar flow para professores acederem a sessões através do código (Polling) [31/5]