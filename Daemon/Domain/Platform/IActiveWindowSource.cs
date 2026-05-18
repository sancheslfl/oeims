namespace Daemon.Domain.Platform
{
    internal interface IActiveWindowSource : IDisposable
    {
        Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct);
    }
}
