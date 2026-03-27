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

            using var processMonitor = new ProcessMonitor();
            processMonitor.StartWatching();

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
