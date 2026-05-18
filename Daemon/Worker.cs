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

            if (serverConfig.ShouldConnect)
            {
                logger.LogInformation("[ServerConnection] Connecting to {BaseUrl}", serverConfig.BaseUrl);
                _ = wsClient.RunAsync(stoppingToken);
                _ = heartbeatSender.RunAsync(stoppingToken);
            }
            else if (!serverConfig.Enabled)
            {
                logger.LogWarning(
                    "[ServerConnection] Disabled by configuration — running without server connection.");
            }
            else
            {
                logger.LogWarning(
                    "[ServerConnection] No server config found — running without server connection. " +
                    "Set Server:BaseUrl, Server:Token and Server:ParticipantId in appsettings.json.");
            }

            await Task.WhenAll(_monitors.Select(m => m.StartAsync(OnEvent, stoppingToken)));
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