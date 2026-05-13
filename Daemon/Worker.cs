using Daemon.Domain;
using Daemon.Monitors;

namespace Daemon
{
    public class Worker(
        IEnumerable<IMonitor> monitors, 
        IEnumerable<IMitigator> mitigators, 
        ILogger<Worker> logger
        ) : BackgroundService
    {
        private readonly IReadOnlyList<IMonitor> _monitors = monitors.ToList();
        private readonly IReadOnlyList<IMitigator> _mitigators = mitigators.ToList();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            Task OnEvent(MonitorEvent e)
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

                return Task.CompletedTask;
            }

            foreach (var mitigator in _mitigators)
            {
                mitigator.Apply();
                logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
            }

            // TODO: Abstract this
            var networkMonitor = _monitors.OfType<NetworkMonitor>().Single();
            await networkMonitor.StartPreExamAsync(OnEvent, stoppingToken);

            await Task.WhenAll(_monitors.Select(m => m.StartAsync(OnEvent, stoppingToken)));
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
