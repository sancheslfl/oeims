using Microsoft.Extensions.Options;

namespace Daemon;

internal sealed class SingleInstanceGuard : IDisposable
{
    private readonly Mutex? _mutex;

    public SingleInstanceGuard(IOptions<ApplicationConfig> config)
    {
        if (config.Value.AllowMultipleInstances)
            return;

        _mutex = new Mutex(
            initiallyOwned: true,
            name: @"Global\OEIMS-Service",
            createdNew: out var createdNew);

        if (!createdNew)
            throw new InvalidOperationException("Another service instance is already running.");
    }

    public void Dispose()
    {
        _mutex?.ReleaseMutex();
        _mutex?.Dispose();
    }
}