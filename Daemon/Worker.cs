using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly FocusMonitor _focusMonitor = new FocusMonitor();
        private readonly ProcessMonitor _processMonitor = new ProcessMonitor();
        private readonly NetworkMonitor _networkMonitor = new NetworkMonitor();

        private void OnNetworkStateChanged(NetworkEvent eventType)
        {
            switch (eventType)
            {
                case NetworkEvent.NetworkChanged:
                    logger.LogWarning("Network change detected!");
                    break;

                case NetworkEvent.MultipleInterfacesDetected:
                    logger.LogWarning("Suspicious interfaces detected!");
                    break;

                case NetworkEvent.MultipleActiveNetworksDetected:
                    logger.LogWarning("Multiple active networks detected!");
                    break;

                case NetworkEvent.NoActiveNetworkDetected:
                    logger.LogWarning("No active network detected!");
                    break;
                default:
                    break;
            }
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            using var clipboardMonitor = new ClipboardMonitor();
            clipboardMonitor.BlockClipboard();

            while (!_networkMonitor.IsValidNetworkState())
            {
                logger.LogWarning("Invalid network state! Student must be in a single physical network");
                // logger.LogWarning("Multi int: {b1}\n Multi net: {b2}\n Not active: {b3}\n Change: {b4}", _networkMonitor.HasMultipleInterfaces(), _networkMonitor.HasMultipleActiveNetworks(), _networkMonitor.HasNoActiveNetworks(), _networkMonitor.HasNetworkChanged());
                await Task.Delay(5000, CancellationToken.None);
            }
            _networkMonitor.InitializeBaseline();

            _networkMonitor.StartMonitoring();
            _networkMonitor.NetworkStateChanged += OnNetworkStateChanged;

            try
            {
                await Task.Delay(Timeout.Infinite, stoppingToken);
            }
            catch (TaskCanceledException)
            {
                // ignore since it occurs when stoppingToken is triggered
            }
            finally
            {
                _networkMonitor.NetworkStateChanged -= OnNetworkStateChanged;
                _networkMonitor.StopMonitoring();
            }
        }
    }
}
