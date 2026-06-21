namespace OEIMS.Sentinel.Agent.Domain
{
    internal interface IActiveWindowSource : IDisposable
    {
        Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct);
    }
}
