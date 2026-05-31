# TODO

## Servidor

- [ ] Investigar que valor usar para `maxFrameSize` na configuração das WebSockets
- [ ] (NEW) Alterar o campo supervisor_id para um array de IDs e suportar vários supervisor para uma sessão
- [ ] (NEW) Permitir que contas de professores acedam a sessões ativas usando os códigos

## Daemon

- [ ] Analisar e melhorar o error handling do Daemon
- [ ] Investigar como tornar os monitores mais robustos
- [ ] Investigar a performance do daemon como serviço do Windows principalmente ao parar
- [ ] Corrigir o problema em que o serviço não interceta nem termina processos proibidos
- [ ] Organizar estrutura da implementação do ClipboardBlocker a nível da plataforma
- [ ] Adicionar verificação de que o daemon está ligado (CONNECTED) antes de considerar o estudante pronto na sessão

## Consola do Professor

- [ ] Adicionar filtro/arquivo para ocultar exames e sessões terminadas
- [ ] Adicionar opção de eliminar ou arquivar exames
- [ ] Criar um campo para a sala no formulário de criação de exame em vez de estar explicito no título
- [ ] Usar reducer hook no componente Dashboard e Login que tem bastantes estados
- [ ] Adicionar integração de OAuth 2.0

## Documentação

- [ ] Reestruturar a secção Introdução e começar com a secção do Enquadramento ou Requisitos Funcionais **(3º prioridade)**
- [ ] Refazer os diagramas com menos ligações e melhor layout **(3º prioridade)**
- [ ] Analisar o código do repositório

## Dúvidas


## Concluído

- [X] Integrar o daemon com WebSockets [12/5]
- [X] Resolver vulnerabilidades do ClipboardBlocker [16/5]
- [X] Evitar o logging repetido e excessivo especialmente no NetworkMonitor e ProcessMonitor [17/5]
- [X] Desenvolver o esqueleto da consola do professor
- [X] Redesenhar o student card para mostrar as infrações inline com scroll em vez de tooltip flutuante (resolve o bug de hover) [27/5]
- [X] Adicionar a claim de email ao JWT para facilitar o logging e rastreabilidade [29/5] (NEW)
