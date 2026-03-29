using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly FocusMonitor _focusMonitor = new FocusMonitor();

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

                await Task.Delay(1000, stoppingToken);
            }
        }
    }
}
