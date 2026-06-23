using Contracts;
using Contracts.Ipc;
using OEIMS.Sentinel.Service.Connections.Agent;
using OEIMS.Sentinel.Service.Connections.Server;
using OEIMS.Sentinel.Service.Monitors;

namespace OEIMS.Sentinel.Service
{
    internal class Worker(
        IEnumerable<IMonitor> monitors,
        IEnumerable<IMitigator> mitigators,
        ServerConfig serverConfig,
        WebSocketClient wsClient,
        AgentPipeServer agentPipeServer,
        HeartbeatSender heartbeatSender,
        ILogger<Worker> logger
        ) : BackgroundService
    {
        private readonly IReadOnlyList<IMonitor> _monitors = monitors.ToList();
        private readonly IReadOnlyList<IMitigator> _mitigators = mitigators.ToList();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            foreach (var mitigator in _mitigators)
            {
                mitigator.Apply();
                logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
            }

            var networkMonitor = _monitors.OfType<NetworkMonitor>().Single();
            await networkMonitor.StartPreExamAsync(OnLocalEvent, stoppingToken);

            var tasks = new List<Task>();

            if (serverConfig.ShouldConnect)
            {
                logger.LogInformation("Connecting to server...");

                tasks.Add(RunComponentAsync(
                    "WebSocket client",
                    wsClient.StartAsync,
                    stoppingToken));

                tasks.Add(RunComponentAsync(
                    "Agent pipe server",
                    ct => agentPipeServer.StartAsync(OnAgentMessage, ct),
                    stoppingToken));

                tasks.Add(RunComponentAsync(
                    "Heartbeat sender",
                    heartbeatSender.StartAsync,
                    stoppingToken));
            }
            else if (!serverConfig.Enabled)
            {
                logger.LogWarning(
                    "Disabled server connection by configuration.");
            }
            else
            {
                logger.LogWarning(
                    "No server config found so running without server connection. " +
                    "Set Server:ApiBaseUrl, Server:RealtimeBaseUrl, Server:Token and Server:ParticipantId in appsettings.json.");
            }

            tasks.AddRange(_monitors.Select(monitor => RunComponentAsync(
                monitor.Name,
                ct => monitor.StartAsync(OnEvent, ct),
                stoppingToken)));

            await Task.WhenAll(tasks);
        }

        private async Task RunComponentAsync(
            string name,
            Func<CancellationToken, Task> runAsync,
            CancellationToken ct)
        {
            try
            {
                logger.LogInformation("Starting component: {name}", name);

                await runAsync(ct);

                logger.LogInformation("Component stopped: {name}", name);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                logger.LogInformation("Component cancelled: {name}", name);
            }
            catch (Exception ex)
            {
                logger.LogCritical(ex, "Component failed: {name}", name);
                throw;
            }
        }

        private Task OnLocalEvent(MonitorEvent e)
        {
            Log(e);
            return Task.CompletedTask;
        }

        private async Task OnEvent(MonitorEvent e)
        {
            Log(e);

            if (serverConfig.ShouldConnect)
                await wsClient.SendEventAsync(e, CancellationToken.None);
        }

        private async Task OnAgentMessage(AgentPipeMessage message)
        {
            switch (message.Type)
            {
                case AgentMessageType.Heartbeat:
                    logger.LogInformation("Agent heartbeat received at {sentAt}", message.SentAt);
                    break;

                case AgentMessageType.Event:
                    if (message.Event is null)
                    {
                        logger.LogWarning("Agent event message ignored because Event was null.");
                        return;
                    }

                    await OnEvent(message.Event);
                    break;

                default:
                    logger.LogWarning("Unknown Agent pipe message type: {type}", message.Type);
                    break;
            }
        }

        private void Log(MonitorEvent e)
        {
            switch (e.Severity)
            {
                case Severity.Info:
                    logger.LogInformation("[{monitor}] {message}", e.MonitorName, e.Message);
                    break;

                case Severity.Warning:
                    logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                    break;

                case Severity.Critical:
                    logger.LogCritical("[{monitor}] {message}", e.MonitorName, e.Message);
                    break;

                default:
                    logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                    break;
            }
        }

        public override async Task StopAsync(CancellationToken cancellationToken)
        {
            await base.StopAsync(cancellationToken);
            await wsClient.DisposeAsync();
        }

        public override void Dispose()
        {
            foreach (var mitigator in _mitigators)
                mitigator.Dispose();

            foreach (var monitor in _monitors)
                monitor.Dispose();

            base.Dispose();
        }
    }
}