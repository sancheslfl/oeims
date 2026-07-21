using Contracts;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using OEIMS.Sentinel.Agent.Ipc;

namespace OEIMS.Sentinel.Agent;

/// <summary>
/// Main background worker for the Sentinel Agent process.
/// </summary>
/// <remarks>
/// The Agent runs in the student desktop session. It applies Agent-side mitigations, starts Agent-side monitors,
/// sends monitor events to the Service, and listens for commands from the Service.
/// </remarks>
/// <param name="monitors">Agent-side monitors registered by dependency injection.</param>
/// <param name="mitigators">Agent-side mitigators registered by dependency injection.</param>
/// <param name="pipeClient">Pipe client used to send heartbeats and monitor events to the Service.</param>
/// <param name="commandPipeServer">Pipe server used to receive commands from the Service.</param>
/// <param name="logger">Worker logger.</param>
internal sealed class Worker(
    IEnumerable<IMonitor> monitors,
    IEnumerable<IMitigator> mitigators,
    AgentEventPipeClient pipeClient,
    AgentCommandPipeServer commandPipeServer,
    ILogger<Worker> logger
) : BackgroundService
{
    private readonly IReadOnlyList<IMonitor> _monitors = monitors.ToList();
    private readonly IReadOnlyList<IMitigator> _mitigators = mitigators.ToList();

    /// <summary>
    /// Starts the Agent runtime until the process is stopped.
    /// </summary>
    /// <param name="stoppingToken">Cancellation token triggered by host shutdown.</param>
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Agent starting...");

        foreach (var mitigator in _mitigators)
        {
            mitigator.Apply();
            logger.LogDebug("Mitigator applied: {Name}", mitigator.Name);
        }

        var tasks = new List<Task>
        {
            RunComponentAsync(
                "Agent heartbeat",
                SendHeartbeatLoopAsync,
                stoppingToken),

            RunComponentAsync(
                "Agent command pipe",
                commandPipeServer.StartAsync,
                stoppingToken)
        };

        tasks.AddRange(_monitors.Select(monitor => RunComponentAsync(
            monitor.Name,
            ct => monitor.StartAsync(OnEvent, ct),
            stoppingToken)));

        await Task.WhenAll(tasks);
    }

    /// <summary>
    /// Sends periodic heartbeats to the Service while the Agent is running.
    /// </summary>
    /// <param name="ct">Cancellation token used to stop the heartbeat loop.</param>
    private async Task SendHeartbeatLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            await pipeClient.SendHeartbeatAsync(ct);
            await Task.Delay(TimeSpan.FromSeconds(5), ct);
        }
    }

    /// <summary>
    /// Runs one long-lived Agent component with consistent lifecycle logging.
    /// </summary>
    /// <param name="name">Component name shown in logs.</param>
    /// <param name="runAsync">Function that starts the component.</param>
    /// <param name="ct">Cancellation token used to stop the component.</param>
    private async Task RunComponentAsync(
        string name,
        Func<CancellationToken, Task> runAsync,
        CancellationToken ct)
    {
        try
        {
            logger.LogDebug("Starting component: {Name}", name);

            await runAsync(ct);

            logger.LogDebug("Component stopped: {Name}", name);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            logger.LogDebug("Component cancelled: {Name}", name);
        }
        catch (Exception ex)
        {
            logger.LogError(
                "Component failed: {Name}. {Message}",
                name,
                ex.Message);
        }
    }

    /// <summary>
    /// Handles one event produced by an Agent-side monitor.
    /// </summary>
    /// <param name="e">Monitor event to log and send to the Service.</param>
    private async Task OnEvent(MonitorEvent e)
    {
        Log(e);

        await pipeClient.SendEventAsync(e, CancellationToken.None);
    }

    private void Log(MonitorEvent e)
    {
        switch (e.Severity)
        {
            case Severity.Info:
                logger.LogInformation("[{Monitor}] {Message}", e.MonitorName, e.Message);
                break;

            case Severity.Warning:
                logger.LogWarning("[{Monitor}] {Message}", e.MonitorName, e.Message);
                break;

            case Severity.Critical:
                logger.LogCritical("[{Monitor}] {Message}", e.MonitorName, e.Message);
                break;

            default:
                logger.LogWarning("[{Monitor}] {Message}", e.MonitorName, e.Message);
                break;
        }
    }

    /// <summary>
    /// Stops the Agent and closes the event pipe client.
    /// </summary>
    public override async Task StopAsync(CancellationToken cancellationToken)
    {
        await base.StopAsync(cancellationToken);
        await pipeClient.DisposeAsync();
    }

    /// <summary>
    /// Disposes mitigators and monitors owned by the Agent worker.
    /// </summary>
    public override void Dispose()
    {
        foreach (var mitigator in _mitigators)
            mitigator.Dispose();

        foreach (var monitor in _monitors)
            monitor.Dispose();

        base.Dispose();
    }
}