using Daemon.Abstractions;
using Daemon.Mitigators;

namespace Daemon
{
    // TODO: Resolve hardcoded monitors and mitigators
    public class Worker(
        IEnumerable<IMonitor> monitors, 
        IEnumerable<IMitigator> mitigators, 
        ILogger<Worker> logger
        ) : BackgroundService
    {
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

            foreach (var mitigator in mitigators)
            {
                mitigator.Apply();
                logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
            }

            var networkMonitor = monitors.OfType<NetworkMonitor>().Single();
            await networkMonitor.StartPreExamAsync(OnEvent, stoppingToken);

            await Task.WhenAll(monitors.Select(m => m.StartAsync(OnEvent, stoppingToken)));
        }

        public override void Dispose()
        {
            foreach (var mitigator in mitigators)
                mitigator.Dispose();

            foreach (var monitor in monitors)
                monitor.Dispose();

            base.Dispose();
        }
    }
}
