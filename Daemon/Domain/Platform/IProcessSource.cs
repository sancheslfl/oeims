namespace Daemon.Domain.Platform;

public interface IProcessSource : IDisposable
{
    Task StartAsync(Func<ProcessInfo, Task> onStarted, CancellationToken ct);

    Task<IReadOnlyList<ProcessKillResult>> KillByNameAsync(
        string processName,
        CancellationToken ct);
}