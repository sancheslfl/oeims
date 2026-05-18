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

    private readonly SemaphoreSlim _killLock = new(1, 1);

    private readonly HashSet<string> _forbiddenProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "slack"
    };

    public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
    {
        try
        {
            var monitoringTask = processSource.StartAsync(
                process => KillIfForbiddenProcessAsync(process, onEvent, ct),
                ct);

            await KillForbiddenProcessesAsync(onEvent, ct);

            await monitoringTask;
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // ignore since normal shutdown.
        }
        catch (Exception ex)
        {
            throw new InvalidOperationException(
                "The process monitor failed to start or stopped unexpectedly.",
                ex);
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
        await _killLock.WaitAsync(ct);

        try
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
                    count: 0,
                    errorMessage: ex.Message));

                return;
            }

            var successfulKills = results
                .Where(result => result.Succeeded)
                .ToList();

            if (successfulKills.Count > 0)
            {
                var displayName = successfulKills[0].ProcessName;

                await onEvent(CreateMonitorEvent(
                    ProcessEvent.ForbiddenProcessKilled,
                    displayName,
                    successfulKills.Count));
            }

            var failedGroups = results
                .Where(result => !result.Succeeded)
                .GroupBy(result => new
                {
                    result.ProcessName,
                    result.ErrorMessage
                });

            foreach (var failedGroup in failedGroups)
            {
                await onEvent(CreateMonitorEvent(
                    ProcessEvent.ForbiddenProcessKillFailed,
                    failedGroup.Key.ProcessName,
                    failedGroup.Count(),
                    failedGroup.Key.ErrorMessage));
            }
        }
        finally
        {
            _killLock.Release();
        }
    }

    private MonitorEvent CreateMonitorEvent(
        ProcessEvent eventType,
        string processName,
        int count,
        string? errorMessage = null)
    {
        var processDescription = count <= 1
            ? processName
            : $"{processName} ({count} instances)";

        return eventType switch
        {
            ProcessEvent.ForbiddenProcessKilled =>
                new MonitorEvent(
                    Name,
                    $"Forbidden process killed: {processDescription}",
                    Severity.Warning),

            ProcessEvent.ForbiddenProcessKillFailed =>
                new MonitorEvent(
                    Name,
                    string.IsNullOrWhiteSpace(errorMessage)
                        ? $"Failed to kill forbidden process: {processDescription}"
                        : $"Failed to kill forbidden process: {processDescription} ({errorMessage})",
                    Severity.Warning),

            _ =>
                new MonitorEvent(
                    Name,
                    $"Unhandled process event: {eventType} ({processDescription})",
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
        _killLock.Dispose();
        processSource.Dispose();
    }
}