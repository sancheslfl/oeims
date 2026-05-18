# TODO

- [X] Integrar o daemon com WebSockets [12/5]
- [X] Resolver vulnerabilidades do ClipboardBlocker [16/5]
- [X] Evitar o logging repetido e excessivo especialmente no NetworkMonitor e ProcessMonitor [17/5]
- [ ] Analisar e melhor o error handling do Daemon
- [ ] Reestruturar a secção Introdução e começar com a secção do Enquadramento ou Requisitos Funcionais **(3º prioridade)**
- [ ] Investigar como tornar os monitores mais robustos
- [ ] Investigar a performance do daemon como serviço do Windows principalmente ao parar
- [ ] Refazer os diagramas com menos ligações e melhor layout **(3º prioridade)**
- [ ] Corrigir o problema em que o serviço não interceta nem termina processos proibidos
- [ ] Investigar que valor usar para `maxFrameSize` na configuração das WebSockets
- [ ] Analisar o código do repositório
- [ ] Desenvolver o esqueleto da consola do professor **(1º prioridade)**
- [ ] Implementar o proprio logger dedicado à comunicação WebSockets **(2º prioridade)**
- [ ] Organizar estrutura da implementaçáo do ClipboardBlocker a nível da plataforma

# Dúvidas

- O que o prof. quis dizer com a implementação do nosso próprio logger?
- Deveremos arranjar uma solução mais robusta para o logging excessivo do FocusMonitor?
