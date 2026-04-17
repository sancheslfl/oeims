using Daemon.Monitors;

namespace Daemon
{
    public class Worker(ILogger<Worker> logger) : BackgroundService
    {
        private readonly FocusMonitor _focusMonitor = new FocusMonitor();
        private readonly NetworkMonitor _networkMonitor = new NetworkMonitor();

        private TaskCompletionSource _validNetworkStateReached = CreateTaskCompletionSource();

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            using var clipboardMonitor = new ClipboardMonitor();
            clipboardMonitor.BlockClipboard();

            _networkMonitor.Start();

            try
            {
                while (true)
                {
                    await WaitForValidNetwork(stoppingToken);
                    _networkMonitor.InitializeBaseline();

                    if (_networkMonitor.IsValidNetworkState())
                        break;

                    logger.LogWarning("Network state changed while initializing baseline. Waiting...");
                }

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
            _networkMonitor.NetworkChanged += OnNetworkChange;

            try
            {
                while (true)
                {
                    if (_networkMonitor.IsValidNetworkState())
                    {
                        logger.LogInformation("Valid network state. Proceeding...");
                        return;
                    }

                    logger.LogWarning("Invalid network state. Guarantee only one physical network connected. Waiting...");

                    var waitForValidNetworkTask = _validNetworkStateReached.Task;
                    await waitForValidNetworkTask.WaitAsync(stoppingToken);

                    if (ReferenceEquals(_validNetworkStateReached.Task, waitForValidNetworkTask))
                    {
                        _validNetworkStateReached = CreateTaskCompletionSource();
                    }
                }
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
            }
        }

        private void OnNetworkChange()
        {
            if (!_networkMonitor.IsValidNetworkState())
            {
                logger.LogWarning("Invalid network state. Guarantee only one physical network connected. Waiting...");
                return;
            }

            logger.LogInformation("Valid network state. Proceeding...");
            _validNetworkStateReached.TrySetResult();
        }

        private static TaskCompletionSource CreateTaskCompletionSource()
        {
            return new(TaskCreationOptions.RunContinuationsAsynchronously);
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
    }
}
