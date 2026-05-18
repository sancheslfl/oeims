using Daemon.Domain;
using Daemon.Domain.Platform;

namespace Daemon.Monitors;

internal enum ProcessEvent
{
    ForbiddenProcessKilled,
    ForbiddenProcessKillFailed
}

internal sealed class ProcessMonitor(IProcessSource processSource) : IMonitor
{
    public string Name => nameof(ProcessMonitor);

    private readonly HashSet<string> _forbiddenProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "slack"
    };

    public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
    {
        var monitoringTask = processSource.StartAsync(
            process => KillIfForbiddenProcessAsync(process, onEvent, ct),
            ct);

        await KillForbiddenProcessesAsync(onEvent, ct);

        try
        {
            await monitoringTask;
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            
        }
    }

    private async Task KillIfForbiddenProcessAsync(
        ProcessInfo process,
        Func<MonitorEvent, Task> onEvent,
        CancellationToken ct)
    {
        var processName = NormalizeProcessName(process.Name);

        if (string.IsNullOrWhiteSpace(processName))
            return;

        if (!_forbiddenProcesses.Contains(processName))
            return;

        await KillProcessByNameAsync(processName, onEvent, ct);
    }

    private async Task KillForbiddenProcessesAsync(
        Func<MonitorEvent, Task> onEvent,
        CancellationToken ct)
    {
        foreach (var forbiddenProcess in _forbiddenProcesses)
        {
            ct.ThrowIfCancellationRequested();

            await KillProcessByNameAsync(forbiddenProcess, onEvent, ct);
        }
    }

    private async Task KillProcessByNameAsync(
        string processName,
        Func<MonitorEvent, Task> onEvent,
        CancellationToken ct)
    {
        IReadOnlyList<ProcessKillResult> results;

        try
        {
            results = await processSource.KillByNameAsync(processName, ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            await onEvent(CreateMonitorEvent(
                ProcessEvent.ForbiddenProcessKillFailed,
                processName,
                ex.Message));

            return;
        }

        foreach (var result in results)
        {
            var eventType = result.Succeeded
                ? ProcessEvent.ForbiddenProcessKilled
                : ProcessEvent.ForbiddenProcessKillFailed;

            await onEvent(CreateMonitorEvent(
                eventType,
                result.ProcessName,
                result.ErrorMessage));
        }
    }

    private MonitorEvent CreateMonitorEvent(
        ProcessEvent eventType,
        string processName,
        string? errorMessage = null)
    {
        return eventType switch
        {
            ProcessEvent.ForbiddenProcessKilled =>
                new MonitorEvent(
                    Name,
                    $"Forbidden process killed: {processName}",
                    Severity.Warning),

            ProcessEvent.ForbiddenProcessKillFailed =>
                new MonitorEvent(
                    Name,
                    string.IsNullOrWhiteSpace(errorMessage)
                        ? $"Failed to kill forbidden process: {processName}"
                        : $"Failed to kill forbidden process: {processName} ({errorMessage})",
                    Severity.Warning),

            _ =>
                new MonitorEvent(
                    Name,
                    $"Unhandled process event: {eventType} ({processName})",
                    Severity.Warning)
        };
    }

    private static string NormalizeProcessName(string processName)
    {
        return Path
            .GetFileNameWithoutExtension(processName)
            .Trim()
            .ToLowerInvariant();
    }

    public void Dispose()
    {
        processSource.Dispose();
    }
}