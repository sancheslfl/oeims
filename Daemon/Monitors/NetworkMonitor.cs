using System.Net.NetworkInformation;
using Daemon.Domain;

namespace Daemon.Monitors
{
    internal record ActiveInterface(string Id, string Name);

    internal enum NetworkEvent
    {
        NetworkChanged,
        MultipleInterfacesDetected,
        MultipleActiveNetworksDetected,
        NoActiveNetworkDetected,
    }

    internal class NetworkMonitor : IMonitor
    {
        public string Name => nameof(NetworkMonitor);

        // TODO: Group this together
        private string? _initialNetworkId;
        private HashSet<ActiveInterface> _initialInterfaces = [];
        private bool _baselineInitialized;

        public void InitializeBaseline()
        {
            _initialNetworkId = GetCurrentNetworkId();
            _initialInterfaces = GetActiveInterfaces();
            _baselineInitialized = true;
        }

        private static readonly HashSet<NetworkInterfaceType> AllowedPhysicalTypes =
        [
            NetworkInterfaceType.Ethernet,
            NetworkInterfaceType.GigabitEthernet,
            NetworkInterfaceType.Wireless80211,
            NetworkInterfaceType.Wwanpp,
            NetworkInterfaceType.Wwanpp2,
            NetworkInterfaceType.Wman
        ];

        public event Action? NetworkChanged;
        public event Action<NetworkEvent>? NetworkViolationDetected;

        public void Start()
        {
            _baselineInitialized = false;
            NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
            NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        }

        public void Stop()
        {
            NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
            NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
            _baselineInitialized = false;
        }

        private void OnNetworkAddressChanged(object? sender, EventArgs e)
        {
            HandleNetworkChange();
        }

        private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
        {
            HandleNetworkChange();
        }

        private void HandleNetworkChange()
        {
            NetworkChanged?.Invoke();
            CheckNetworkViolation();
        }

        private void CheckNetworkViolation()
        {
            if (!_baselineInitialized)
                return;

            if (HasNetworkChanged())
                NetworkViolationDetected?.Invoke(NetworkEvent.NetworkChanged);

            if (HasMultipleInterfaces())
                NetworkViolationDetected?.Invoke(NetworkEvent.MultipleInterfacesDetected);

            if (HasMultipleActiveNetworks())
                NetworkViolationDetected?.Invoke(NetworkEvent.MultipleActiveNetworksDetected);

            if (HasNoActiveNetworks())
                NetworkViolationDetected?.Invoke(NetworkEvent.NoActiveNetworkDetected);

        }

        public bool HasNetworkChanged()
        {
            var current = GetCurrentNetworkId();
            return current != _initialNetworkId;
        }

        public bool HasMultipleInterfaces()
        {
            var current = GetActiveInterfaces();
            return !_initialInterfaces.SetEquals(current);
        }

        public bool HasMultipleActiveNetworks()
        {
            var count = GetActiveInterfaces().Count;
            return count > 1;
        }

        public bool HasNoActiveNetworks()
        {
            var count = GetActiveInterfaces().Count;
            return count == 0;
        }

        public bool IsValidNetworkState()
        {
            return GetActivePhysicalInterfaces().Count == 1;
        }

        public async Task StartPreExamAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
        {
            Start();

            try
            {
                while (!ct.IsCancellationRequested)
                {
                    if (!IsValidNetworkState())
                    {
                        await onEvent(new MonitorEvent(Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));
                        await WaitForValidNetworkAsync(ct);
                    }

                    InitializeBaseline();

                    if (IsValidNetworkState())
                    {
                        await onEvent(new MonitorEvent(Name, "Valid network state and baseline initialized. Proceeding to exam.", Severity.Info));
                        return;
                    }

                    await onEvent(new MonitorEvent(Name, "Network state changed while initializing baseline. Waiting...", Severity.Warning));
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                Stop();
                throw;
            }
        }

        public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
        {
            if (!_baselineInitialized)
                throw new InvalidOperationException("Pre-exam validation must run before exam monitoring starts.");

            Action<NetworkEvent> onViolation = eventType =>
            {
                _ = onEvent(CreateMonitorEvent(eventType)).ContinueWith(    // TODO: evaluate this
                    static t => _ = t.Exception,
                    CancellationToken.None,
                    TaskContinuationOptions.OnlyOnFaulted,
                    TaskScheduler.Default);
            };

            NetworkViolationDetected += onViolation;

            try
            {
                await Task.Delay(Timeout.Infinite, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                // ignore
            }
            finally
            {
                NetworkViolationDetected -= onViolation;
                Stop();
            }
        }

        private string GetCurrentNetworkId()
        {
            var active = NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                            n.NetworkInterfaceType != NetworkInterfaceType.Loopback);

            return string.Join("|", active.Select(n =>
            {
                var ipProps = n.GetIPProperties();
                var gateway = ipProps.GatewayAddresses
                    .FirstOrDefault()?.Address.ToString() ?? "no-gw";

                return $"{n.Name}-{gateway}";
            }));
        }

        private static HashSet<ActiveInterface> GetActiveInterfaces()
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(n =>
                        n.OperationalStatus == OperationalStatus.Up &&
                        n.NetworkInterfaceType != NetworkInterfaceType.Loopback &&
                        n.GetIPProperties().GatewayAddresses.Count != 0
                )
                .Select(n => new ActiveInterface(n.Id, n.Name))
                .ToHashSet();
        }

        private static HashSet<ActiveInterface> GetActivePhysicalInterfaces()
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(n =>
                    n.OperationalStatus == OperationalStatus.Up &&
                    AllowedPhysicalTypes.Contains(n.NetworkInterfaceType) &&
                    n.GetIPProperties().GatewayAddresses.Count != 0
                )
                .Select( n => new ActiveInterface(n.Id, n.Name))
                .ToHashSet();
        }

        private async Task WaitForValidNetworkAsync(CancellationToken ct)
        {
            if (IsValidNetworkState())
                return;

            var tcs = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);

            void OnChange()
            {
                if (IsValidNetworkState())
                    tcs.TrySetResult();
            }

            NetworkChanged += OnChange;
            try
            {
                await tcs.Task.WaitAsync(ct);
            }
            finally
            {
                NetworkChanged -= OnChange;
            }
        }

        private MonitorEvent CreateMonitorEvent(NetworkEvent eventType)
        {
            return eventType switch
            {
                NetworkEvent.NetworkChanged => new MonitorEvent(Name, "Network change detected!", Severity.Warning),
                NetworkEvent.MultipleInterfacesDetected => new MonitorEvent(Name, "Suspicious interfaces detected!", Severity.Warning),
                NetworkEvent.MultipleActiveNetworksDetected => new MonitorEvent(Name, "Multiple active networks detected!", Severity.Warning),
                NetworkEvent.NoActiveNetworkDetected => new MonitorEvent(Name, "No active network detected!", Severity.Warning),
                _ => new MonitorEvent(Name, $"Unhandled network event: {eventType}", Severity.Warning)
            };
        }

        public void Dispose()
        {
            Stop();
        }
    }
}
