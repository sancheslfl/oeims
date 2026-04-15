using System.Net.NetworkInformation;
using Daemon.Abstractions;

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
        public string Name => "NetworkMonitor";

        private string? _initialNetworkId;
        private HashSet<ActiveInterface> _initialInterfaces = [];

        public void InitializeBaseline()
        {
            _initialNetworkId = GetCurrentNetworkId();
            _initialInterfaces = GetActiveInterfaces();
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
            NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
            NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        }

        public void Stop()
        {
            NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
            NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
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

        public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
        {
            Start();

            Action<NetworkEvent> onViolation = eventType => _ = HandleViolationEventAsync(eventType, onEvent);

            try
            {
                if (!IsValidNetworkState())
                {
                    await onEvent(new MonitorEvent(Name, "Invalid network state. Guarantee only one physical network connected. Waiting...", Severity.Warning));
                    await WaitForValidNetworkAsync(ct);
                    await onEvent(new MonitorEvent(Name, "Valid network state. Proceeding...", Severity.Info));
                }

                InitializeBaseline();
                NetworkViolationDetected += onViolation;

                await Task.Delay(Timeout.Infinite, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                return;
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

        private async Task HandleViolationEventAsync(NetworkEvent eventType, Func<MonitorEvent, Task> onEvent)
        {
            try
            {
                var monitorEvent = eventType switch
                {
                    NetworkEvent.NetworkChanged => new MonitorEvent(Name, "Network change detected!", Severity.Warning),
                    NetworkEvent.MultipleInterfacesDetected => new MonitorEvent(Name, "Suspicious interfaces detected!", Severity.Warning),
                    NetworkEvent.MultipleActiveNetworksDetected => new MonitorEvent(Name, "Multiple active networks detected!", Severity.Warning),
                    NetworkEvent.NoActiveNetworkDetected => new MonitorEvent(Name, "No active network detected!", Severity.Warning),
                    _ => new MonitorEvent(Name, $"Unhandled network event: {eventType}", Severity.Warning)
                };

                await onEvent(monitorEvent);
            }
            catch
            {
            }
        }

        public void Dispose() { }
    }
}
