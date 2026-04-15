using Daemon.Abstractions;
using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly NetworkMonitor _networkMonitor = new NetworkMonitor();

        private readonly TaskCompletionSource _tcs = new TaskCompletionSource();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            using var clipboardMonitor = new ClipboardMonitor();
            clipboardMonitor.BlockClipboard();

            _networkMonitor.Start();

            try
            {
                await WaitForValidNetwork(stoppingToken);

                _networkMonitor.InitializeBaseline();
                SubscribeEvents();

                await WaitForShutdown(stoppingToken);
            }
            finally
            {
                UnsubscribeEvents();
                _networkMonitor.Stop();
            }
        }

        private async Task WaitForValidNetwork(CancellationToken stoppingToken)
        {
            if (_networkMonitor.IsValidNetworkState())
            {
                _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Valid network state. Proceeding...", Severity.Info));
                return;
            }

            _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));

            _networkMonitor.NetworkChanged += OnNetworkChange;

            try
            {
                await _tcs.Task.WaitAsync(stoppingToken);
            }
            finally
            {
                _networkMonitor.NetworkChanged -= OnNetworkChange;
            }
        }

        private void OnNetworkViolationDetected(NetworkEvent eventType)
        {
            switch (eventType)
            {
                case NetworkEvent.NetworkChanged:
                    _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Network change detected!", Severity.Warning));
                    break;

                case NetworkEvent.MultipleInterfacesDetected:
                    _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Suspicious interfaces detected!", Severity.Warning));
                    break;

                case NetworkEvent.MultipleActiveNetworksDetected:
                    _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Multiple active networks detected!", Severity.Warning));
                    break;

                case NetworkEvent.NoActiveNetworkDetected:
                    _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "No active network detected!", Severity.Warning));
                    break;
            }
        }

        private void OnNetworkChange()
        {
            if (!_networkMonitor.IsValidNetworkState())
            {
                _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));
                return;
            }

            _ = OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Valid network state. Proceeding...", Severity.Info));
            _tcs.TrySetResult();
        }

        private static async Task WaitForShutdown(CancellationToken stoppingToken)
        {
            await Task.Delay(Timeout.Infinite, stoppingToken);
        }

        private void SubscribeEvents()
        {
            _networkMonitor.NetworkViolationDetected += OnNetworkViolationDetected;
        }

        private void UnsubscribeEvents()
        {
            _networkMonitor.NetworkViolationDetected -= OnNetworkViolationDetected;
        }

        private Task OnMonitorEvent(MonitorEvent e)
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
    }
}
