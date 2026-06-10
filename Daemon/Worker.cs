using Daemon.Domain;
using Daemon.Monitors;
using Daemon.ServerConnection;

namespace Daemon
{
    internal class Worker(
        IEnumerable<IMonitor> monitors,
        IEnumerable<IMitigator> mitigators,
        ServerConfig serverConfig,
        DaemonWebSocketClient wsClient,
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
                    wsClient.RunAsync,
                    stoppingToken));

                tasks.Add(RunComponentAsync(
                    "Heartbeat sender",
                    heartbeatSender.RunAsync,
                    stoppingToken));
            }
            else if (!serverConfig.Enabled)
            {
                logger.LogWarning(
                    "Disabled by configuration - running without server connection.");
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