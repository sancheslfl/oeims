using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly FocusMonitor _focusMonitor = new FocusMonitor();
        private readonly NetworkMonitor _networkMonitor = new NetworkMonitor();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            using var clipboardMonitor = new ClipboardMonitor();
            clipboardMonitor.BlockClipboard();

            using var processMonitor = new ProcessMonitor();
            processMonitor.StartWatching();

            using var processBlocker = new ProcessBlocker();
            var blocked = processBlocker.BlockForbiddenProcesses();
            foreach (var process in blocked)
            {
                logger.LogWarning("Blocked process: {process}", process);
            }

            while (!_networkMonitor.IsValidNetworkState())
            {
                logger.LogWarning("Invalid network state! Student must be connected to a single physical network");
                logger.LogWarning("Multi int: {b1}\n Multi net: {b2}\n Not active: {b3}\n Change: {b4}",
                    _networkMonitor.HasMultipleInterfaces(),
                    _networkMonitor.HasMultipleActiveNetworks(),
                    _networkMonitor.HasNoActiveNetworks(),
                    _networkMonitor.HasNetworkChanged());
                await Task.Delay(5000, CancellationToken.None);
            }
            _networkMonitor.InitializeBaseline();

            while (!stoppingToken.IsCancellationRequested)
            {
                if (!_focusMonitor.IsExamWindowFocused())
                {
                    logger.LogWarning("Focus lost! Current window: {title}", _focusMonitor.GetForegroundWindowTitle());
                }

                var killed = processMonitor.KillForbiddenProcesses();
                if (killed.Any())
                {
                    logger.LogWarning("Forbidden processes killed: {processes}", string.Join(", ", killed));
                }

                if (_networkMonitor.HasNetworkChanged())
                {
                    logger.LogWarning("Network change detected!");
                }

                if (_networkMonitor.HasMultipleInterfaces())
                {
                    logger.LogWarning("Suspicious interfaces detected!");
                }

                if (_networkMonitor.HasMultipleActiveNetworks())
                {
                    logger.LogWarning("Multiple active networks detected!");
                }

                if (_networkMonitor.HasNoActiveNetworks())
                {
                    logger.LogWarning("No active network was detected!");
                }

                await Task.Delay(1000, stoppingToken);
            }
        }
    }
}