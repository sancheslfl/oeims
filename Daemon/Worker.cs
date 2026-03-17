using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly FocusMonitor _focusMonitor = new FocusMonitor();
        private readonly ProcessMonitor _processMonitor = new ProcessMonitor();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            using var clipboardMonitor = new ClipboardMonitor();
            clipboardMonitor.BlockClipboard();

            while (!stoppingToken.IsCancellationRequested)
            {
                if (!_focusMonitor.IsExamWindowFocused())
                {
                    logger.LogWarning("Focus lost! Current window: {title}", _focusMonitor.GetForegroundWindowTitle());
                }

                var forbidden = _processMonitor.GetForbiddenProcesses();
                if (forbidden.Any()) {
                    logger.LogWarning("Forbidden processes detected: {processes}", string.Join(", ", forbidden));
                    _processMonitor.KillForbiddenProcesses();
                }
                await Task.Delay(1000, stoppingToken);
            }
        }
    }
}
