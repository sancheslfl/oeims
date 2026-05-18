namespace Daemon.Domain
{
    public interface IMonitor : IDisposable
    {
        string Name { get; }
        Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct);
    }
}
