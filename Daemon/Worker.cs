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
            // ── Mitigators ────────────────────────────────────────────────────────
            foreach (var mitigator in _mitigators)
            {
                mitigator.Apply();
                logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
            }

            // ── Pre-exam network validation ───────────────────────────────────────
            var networkMonitor = _monitors.OfType<NetworkMonitor>().Single();
            await networkMonitor.StartPreExamAsync(OnLocalEvent, stoppingToken);

            // ── Server communication ──────────────────────────────────────────────
            if (serverConfig.IsConfigured)
            {
                logger.LogInformation("[ServerConnection] Connecting to {BaseUrl}", serverConfig.BaseUrl);
                _ = wsClient.RunAsync(stoppingToken);
                _ = heartbeatSender.RunAsync(stoppingToken);
            }
            else
            {
                logger.LogWarning(
                    "[ServerConnection] No server config found — running in standalone mode. " +
                    "Set Server:BaseUrl, Server:Token and Server:ParticipantId in appsettings.json.");
            }

            // ── Monitors ──────────────────────────────────────────────────────────
            await Task.WhenAll(_monitors.Select(m => m.StartAsync(OnEvent, stoppingToken)));
        }

        // Used during pre-exam phase — logs locally only, WS not open yet.
        private Task OnLocalEvent(MonitorEvent e)
        {
            Log(e);
            return Task.CompletedTask;
        }

        // Used during exam — logs locally and forwards to the server.
        private async Task OnEvent(MonitorEvent e)
        {
            Log(e);

            if (serverConfig.IsConfigured)
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
