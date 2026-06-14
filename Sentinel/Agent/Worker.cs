using Contracts;

namespace OEIMS.Sentinel.Agent;

internal sealed class Worker(
    IEnumerable<IMonitor> monitors,
    IEnumerable<IMitigator> mitigators,
    ILogger<Worker> logger
) : BackgroundService
{
    private readonly IReadOnlyList<IMonitor> _monitors = monitors.ToList();
    private readonly IReadOnlyList<IMitigator> _mitigators = mitigators.ToList();

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Sentinel Agent starting...");

        foreach (var mitigator in _mitigators)
        {
            mitigator.Apply();
            logger.LogInformation("Mitigator applied: {name}", mitigator.Name);
        }

        var tasks = _monitors
            .Select(monitor => RunComponentAsync(
                monitor.Name,
                ct => monitor.StartAsync(OnEvent, ct),
                stoppingToken))
            .ToList();

        if (tasks.Count == 0)
        {
            logger.LogWarning("No agent monitors registered.");

            await Task.Delay(Timeout.InfiniteTimeSpan, stoppingToken);
            return;
        }

        await Task.WhenAll(tasks);
    }

    private async Task RunComponentAsync(
        string name,
        Func<CancellationToken, Task> runAsync,
        CancellationToken ct)
    {
        try
        {
            logger.LogInformation("Starting component: {name}", name);

            await runAsync(ct);

            logger.LogInformation("Component stopped: {name}", name);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            logger.LogInformation("Component cancelled: {name}", name);
        }
        catch (Exception ex)
        {
            logger.LogCritical(ex, "Component failed: {name}", name);
            throw;
        }
    }

    private async Task OnEvent(MonitorEvent e)
    {
        Log(e);

    }

    private void Log(MonitorEvent e)
    {
        switch (e.Severity)
        {
            case Severity.Info:
                logger.LogInformation("[{monitor}] {message}", e.MonitorName, e.Message);
                break;

            case Severity.Warning:
                logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                break;

            case Severity.Critical:
                logger.LogCritical("[{monitor}] {message}", e.MonitorName, e.Message);
                break;

            default:
                logger.LogWarning("[{monitor}] {message}", e.MonitorName, e.Message);
                break;
        }
    }

    public override void Dispose()
    {
        foreach (var mitigator in _mitigators)
            mitigator.Dispose();

        foreach (var monitor in _monitors)
            monitor.Dispose();

        base.Dispose();
    }
}