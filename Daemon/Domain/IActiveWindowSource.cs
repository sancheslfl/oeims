namespace Daemon.Domain
{
    internal interface IActiveWindowSource : IDisposable
    {
        Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct);
    }
}
