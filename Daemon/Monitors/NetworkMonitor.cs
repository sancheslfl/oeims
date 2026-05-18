using System.Net.NetworkInformation;
using Daemon.Domain;

namespace Daemon.Monitors;

internal sealed record ActiveInterface(string Id, string Name);

internal sealed record NetworkViolation(
    string Key,
    MonitorEvent Event);

internal sealed record NetworkState(
    HashSet<ActiveInterface> ActiveInterfaces,
    HashSet<ActiveInterface> ActivePhysicalInterfaces,
    string NetworkId);

internal sealed class NetworkMonitor : IMonitor
{
    public string Name => nameof(NetworkMonitor);

    private static readonly HashSet<NetworkInterfaceType> AllowedPhysicalTypes =
    [
        NetworkInterfaceType.Ethernet,
        NetworkInterfaceType.GigabitEthernet,
        NetworkInterfaceType.Wireless80211,
        NetworkInterfaceType.Wwanpp,
        NetworkInterfaceType.Wwanpp2,
        NetworkInterfaceType.Wman
    ];

    private NetworkState? _baseline;
    private string? _lastViolationKey;
    private bool _started;

    public event Action? NetworkChanged;
    public event Action<MonitorEvent>? NetworkViolationDetected;

    public void InitializeBaseline()
    {
        _baseline = GetCurrentNetworkState();
        _lastViolationKey = null;
    }

    public void Start()
    {
        if (_started)
            return;

        _baseline = null;
        _lastViolationKey = null;

        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;

        _started = true;
    }

    public void Stop()
    {
        if (!_started)
            return;

        NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;

        _baseline = null;
        _lastViolationKey = null;
        _started = false;
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
        if (_baseline is null)
            return;

        var violation = GetCurrentViolation();

        if (violation is null)
        {
            _lastViolationKey = null;
            return;
        }

        if (violation.Key == _lastViolationKey)
            return;

        _lastViolationKey = violation.Key;

        NetworkViolationDetected?.Invoke(violation.Event);
    }

    private NetworkViolation? GetCurrentViolation()
    {
        if (_baseline is null)
            return null;

        var currentState = GetCurrentNetworkState();

        var baselineInterfaces = FormatInterfaces(_baseline.ActiveInterfaces);
        var activeInterfaces = FormatInterfaces(currentState.ActiveInterfaces);

        return currentState switch
        {
            _ when HasNoActiveNetwork(currentState) =>
                new NetworkViolation(
                    Key: "NoActiveNetwork",
                    Event: new MonitorEvent(
                        Name,
                        $"Network disconnected: no active network interface. Baseline was: {baselineInterfaces}.",
                        Severity.Warning)),

            _ when HasNoPhysicalInterface(currentState) =>
                new NetworkViolation(
                    Key: $"NoPhysicalInterface:{activeInterfaces}",
                    Event: new MonitorEvent(
                        Name,
                        $"Invalid network state: active interfaces exist, but none are physical. Active interfaces: {activeInterfaces}. Baseline was: {baselineInterfaces}.",
                        Severity.Warning)),

            _ when HasMultipleActiveInterfaces(currentState) =>
                new NetworkViolation(
                    Key: $"MultipleInterfaces:{activeInterfaces}",
                    Event: new MonitorEvent(
                        Name,
                        $"Multiple or suspicious active interfaces detected: {activeInterfaces}. Only one physical interface is allowed. Baseline was: {baselineInterfaces}.",
                        Severity.Warning)),

            _ when HasInterfaceChanged(_baseline, currentState) =>
                new NetworkViolation(
                    Key: $"InterfacesChanged:{activeInterfaces}",
                    Event: new MonitorEvent(
                        Name,
                        $"Network interface changed: baseline was {baselineInterfaces}, current is {activeInterfaces}.",
                        Severity.Warning)),

            _ when HasNetworkIdentityChanged(_baseline, currentState) =>
                new NetworkViolation(
                    Key: $"NetworkChanged:{currentState.NetworkId}",
                    Event: new MonitorEvent(
                        Name,
                        $"Network changed on the same interface: baseline was {_baseline.NetworkId}, current is {currentState.NetworkId}.",
                        Severity.Warning)),

            _ => null
        };
    }

    public bool IsValidNetworkState()
    {
        return IsValidNetworkState(GetCurrentNetworkState());
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
                    await onEvent(new MonitorEvent(
                        Name,
                        "Invalid network state. Exactly one active network interface is allowed, and it must be physical. Disable VPNs, virtual adapters, or extra network connections. Waiting...",
                        Severity.Warning));

                    await WaitForValidNetworkAsync(ct);
                }

                InitializeBaseline();

                if (IsValidNetworkState())
                {
                    await onEvent(new MonitorEvent(
                        Name,
                        "Valid network state and baseline initialized. Proceeding to exam.",
                        Severity.Info));

                    return;
                }

                await onEvent(new MonitorEvent(
                    Name,
                    "Network state changed while initializing baseline. Waiting...",
                    Severity.Warning));
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
        if (_baseline is null)
            throw new InvalidOperationException("Pre-exam validation must run before exam monitoring starts.");

        Action<MonitorEvent> onViolation = monitorEvent =>
        {
            _ = NotifyAsync(monitorEvent, onEvent);
        };

        NetworkViolationDetected += onViolation;

        try
        {
            await Task.Delay(Timeout.Infinite, ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // ignore since normal shutdown.
        }
        finally
        {
            NetworkViolationDetected -= onViolation;
            Stop();
        }
    }

    private static async Task NotifyAsync(
        MonitorEvent monitorEvent,
        Func<MonitorEvent, Task> onEvent)
    {
        try
        {
            await onEvent(monitorEvent);
        }
        catch
        {
            // ignore because the network callback should not crash because an event consumer failed.
        }
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

    private static NetworkState GetCurrentNetworkState()
    {
        var activeInterfaces = NetworkInterface.GetAllNetworkInterfaces()
            .Where(IsActiveInterface)
            .ToList();

        return new NetworkState(
            ActiveInterfaces: activeInterfaces
                .Select(ToActiveInterface)
                .ToHashSet(),
            ActivePhysicalInterfaces: activeInterfaces
                .Where(IsPhysicalInterface)
                .Select(ToActiveInterface)
                .ToHashSet(),
            NetworkId: string.Join("|", activeInterfaces.Select(GetNetworkIdentity)));
    }

    private static bool IsValidNetworkState(NetworkState state)
    {
        return state.ActiveInterfaces.Count == 1 &&
               state.ActivePhysicalInterfaces.Count == 1;
    }

    private static bool HasNoActiveNetwork(NetworkState state)
    {
        return state.ActiveInterfaces.Count == 0;
    }

    private static bool HasNoPhysicalInterface(NetworkState state)
    {
        return state.ActivePhysicalInterfaces.Count == 0;
    }

    private static bool HasMultipleActiveInterfaces(NetworkState state)
    {
        return state.ActiveInterfaces.Count > 1;
    }

    private static bool HasInterfaceChanged(
        NetworkState baseline,
        NetworkState current)
    {
        return !baseline.ActiveInterfaces.SetEquals(current.ActiveInterfaces);
    }

    private static bool HasNetworkIdentityChanged(
        NetworkState baseline,
        NetworkState current)
    {
        return current.NetworkId != baseline.NetworkId;
    }

    private static bool IsActiveInterface(NetworkInterface networkInterface)
    {
        return networkInterface.OperationalStatus == OperationalStatus.Up &&
               networkInterface.NetworkInterfaceType != NetworkInterfaceType.Loopback &&
               HasGateway(networkInterface);
    }

    private static bool IsPhysicalInterface(NetworkInterface networkInterface)
    {
        return AllowedPhysicalTypes.Contains(networkInterface.NetworkInterfaceType);
    }

    private static bool HasGateway(NetworkInterface networkInterface)
    {
        try
        {
            return networkInterface.GetIPProperties().GatewayAddresses.Count != 0;
        }
        catch (NetworkInformationException)
        {
            return false;
        }
    }

    private static ActiveInterface ToActiveInterface(NetworkInterface networkInterface)
    {
        return new ActiveInterface(
            networkInterface.Id,
            networkInterface.Name);
    }

    private static string GetNetworkIdentity(NetworkInterface networkInterface)
    {
        return $"{networkInterface.Name}-{GetGatewayAddress(networkInterface)}";
    }

    private static string GetGatewayAddress(NetworkInterface networkInterface)
    {
        try
        {
            return networkInterface
                .GetIPProperties()
                .GatewayAddresses
                .FirstOrDefault()
                ?.Address
                .ToString() ?? "no-gw";
        }
        catch (NetworkInformationException)
        {
            return "unknown-gw";
        }
    }

    private static string FormatInterfaces(IEnumerable<ActiveInterface> interfaces)
    {
        var names = interfaces
            .Select(i => i.Name)
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .OrderBy(name => name)
            .ToList();

        return names.Count == 0
            ? "none"
            : string.Join(", ", names);
    }

    public void Dispose()
    {
        Stop();
    }
}