using Daemon.Abstractions;
using Daemon.Monitors;

namespace Daemon
{
    public class Worker : BackgroundService
    {
        private readonly ILogger<Worker> logger;

        private readonly FocusMonitor _focusMonitor = new FocusMonitor();
        private readonly ProcessMonitor _processMonitor = new ProcessMonitor();
        private readonly NetworkMonitor _networkMonitor = new NetworkMonitor();
        private readonly ClipboardMonitor _clipboardMonitor = new ClipboardMonitor();
        private readonly ProcessBlocker _processBlocker = new ProcessBlocker();

        private readonly List<(string Name, Func<CancellationToken, Task> Run)> _monitors;
        private readonly List<(string Name, Action Apply)> _mitigators;

        private readonly TaskCompletionSource _tcs = new TaskCompletionSource();

        public Worker(ILogger<Worker> logger)
        {
            this.logger = logger;

            _monitors =
            [
                (nameof(FocusMonitor), RunFocusMonitorAsync),
                (nameof(ProcessMonitor), RunProcessMonitorAsync),
                (nameof(NetworkMonitor), RunNetworkMonitorAsync),
            ];

            _mitigators =
            [
                (nameof(ClipboardMonitor), _clipboardMonitor.BlockClipboard),
                (nameof(ProcessBlocker), () => _ = _processBlocker.BlockForbiddenProcesses()),
            ];
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            ApplyMitigators();
            var monitorTasks = _monitors.Select(m => StartMonitorAsync(m, stoppingToken));

            try
            {
                await Task.WhenAll(monitorTasks);
            }
            finally
            {
                Cleanup();
            }
        }

        private void ApplyMitigators()
        {
            foreach (var mitigator in _mitigators)
            {
                mitigator.Apply();
                OnMonitorEvent(new MonitorEvent(mitigator.Name, $"{mitigator.Name} applied.", Severity.Info));
            }
        }

        private async Task StartMonitorAsync((string Name, Func<CancellationToken, Task> Run) monitor, CancellationToken stoppingToken)
        {
            OnMonitorEvent(new MonitorEvent(monitor.Name, $"{monitor.Name} starting.", Severity.Info));
            await monitor.Run(stoppingToken);
        }

        private async Task RunNetworkMonitorAsync(CancellationToken stoppingToken)
        {
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
                OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Valid network state. Proceeding...", Severity.Info));
                return;
            }

            OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));

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
                    OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Network change detected!", Severity.Warning));
                    break;

                case NetworkEvent.MultipleInterfacesDetected:
                    OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Suspicious interfaces detected!", Severity.Warning));
                    break;

                case NetworkEvent.MultipleActiveNetworksDetected:
                    OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Multiple active networks detected!", Severity.Warning));
                    break;

                case NetworkEvent.NoActiveNetworkDetected:
                    OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "No active network detected!", Severity.Warning));
                    break;
            }
        }

        private void OnNetworkChange()
        {
            if (!_networkMonitor.IsValidNetworkState())
            {
                OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));
                return;
            }

            OnMonitorEvent(new MonitorEvent(_networkMonitor.Name, "Valid network state. Proceeding...", Severity.Info));
            _tcs.TrySetResult();
        }

        private async Task RunFocusMonitorAsync(CancellationToken stoppingToken)
        {
            try
            {
                while (!stoppingToken.IsCancellationRequested)
                {
                    if (!_focusMonitor.IsExamWindowFocused())
                    {
                        var title = _focusMonitor.GetForegroundWindowTitle();
                        OnMonitorEvent(new MonitorEvent(nameof(FocusMonitor), $"Focus lost: {title}", Severity.Warning));
                    }

                    await Task.Delay(1000, stoppingToken);
                }
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
        }

        private async Task RunProcessMonitorAsync(CancellationToken stoppingToken)
        {
            _processMonitor.StartWatching();

            try
            {
                while (!stoppingToken.IsCancellationRequested)
                {
                    var killed = _processMonitor.KillForbiddenProcesses();
                    foreach (var process in killed)
                    {
                        OnMonitorEvent(new MonitorEvent(nameof(ProcessMonitor), $"Forbidden process killed: {process}", Severity.Warning));
                    }

                    await Task.Delay(1000, stoppingToken);
                }
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
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

        private void OnMonitorEvent(MonitorEvent e)
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

        private void Cleanup()
        {
            _processMonitor.Dispose();
            _networkMonitor.Dispose();
            _processBlocker.Dispose();
            _clipboardMonitor.Dispose();
        }
    }
}
