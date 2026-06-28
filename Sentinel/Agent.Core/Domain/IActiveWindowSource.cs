namespace OEIMS.Sentinel.Agent.Domain
{
    public interface IActiveWindowSource : IDisposable
    {
        Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct);
    }
}
