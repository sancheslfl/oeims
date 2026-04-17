using Daemon.Abstractions;
using Daemon.Monitors;

namespace Daemon
{
    public class Worker : BackgroundService
    {
        private readonly ILogger<Worker> _logger;
        private readonly NetworkMonitor _networkMonitor = new();
        private int _networkReady;

        private readonly List<IMonitor> _monitors;
        private readonly List<IMitigator> _mitigators =
        [
            new ClipboardMonitor(),
            new ProcessBlocker(),
        ];

        public Worker(ILogger<Worker> logger)
        {
            _logger = logger;
            _monitors =
            [
                new FocusMonitor(),
                new ProcessMonitor(),
                _networkMonitor,
            ];
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            Task OnEvent(MonitorEvent e)
            {
                if (e.MonitorName == _networkMonitor.Name &&
                    e.Severity == Severity.Info &&
                    e.Message == "Valid network state. Proceeding...")
                {
                    Interlocked.Exchange(ref _networkReady, 1);
                }

                if (Volatile.Read(ref _networkReady) == 0 && e.MonitorName != _networkMonitor.Name)
                    return Task.CompletedTask;

                switch (e.Severity)
                {
                    case Severity.Info:
                        _logger.LogInformation("[{monitor}] {message}", e.MonitorName, e.Message);
                        break;
                    case Severity.Warning:
                        _logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                        break;
                    case Severity.Critical:
                        _logger.LogCritical("[{monitor}] {message}", e.MonitorName, e.Message);
                        break;
                    default:
                        _logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                        break;
                }

                return Task.CompletedTask;
            }

            foreach (var mitigator in _mitigators)
            {
                mitigator.Apply();
                _logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
            }

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
