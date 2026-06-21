using System.Diagnostics;
using System.Globalization;
using System.Management;
using OEIMS.Sentinel.Service.Domain.Platform;

namespace OEIMS.Sentinel.Service.Platform.Windows;

internal sealed class WindowsProcessSource : IProcessSource
{
    private ManagementEventWatcher? _watcher;
    private EventArrivedEventHandler? _eventHandler;
    private Func<ProcessInfo, Task>? _onStarted;

    public async Task StartAsync(Func<ProcessInfo, Task> onStarted, CancellationToken ct)
    {
        _onStarted = onStarted;

        _watcher = new ManagementEventWatcher(
            new WqlEventQuery("SELECT * FROM Win32_ProcessStartTrace"));

        _eventHandler = OnProcessStarted;
        _watcher.EventArrived += _eventHandler;

        try
        {
            _watcher.Start();

            await Task.Delay(Timeout.InfiniteTimeSpan, ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {

        }
        finally
        {
            StopWatcher();
        }
    }

    public Task<IReadOnlyList<ProcessKillResult>> KillByNameAsync(
        string processName,
        CancellationToken ct)
    {
        var results = new List<ProcessKillResult>();

        Process[] processes;

        try
        {
            processes = Process.GetProcessesByName(processName);
        }
        catch (Exception ex)
        {
            results.Add(new ProcessKillResult(
                processName,
                ProcessId: null,
                Succeeded: false,
                ErrorMessage: ex.Message));

            return Task.FromResult<IReadOnlyList<ProcessKillResult>>(results);
        }

        foreach (var process in processes)
        {
            ct.ThrowIfCancellationRequested();

            using (process)
            {
                var currentProcessName = process.ProcessName;
                var processId = TryGetProcessId(process);

                try
                {
                    process.Kill(entireProcessTree: true);

                    results.Add(new ProcessKillResult(
                        currentProcessName,
                        processId,
                        Succeeded: true));
                }
                catch (Exception ex)
                {
                    results.Add(new ProcessKillResult(
                        currentProcessName,
                        processId,
                        Succeeded: false,
                        ErrorMessage: ex.Message));
                }
            }
        }

        return Task.FromResult<IReadOnlyList<ProcessKillResult>>(results);
    }

    private void OnProcessStarted(object sender, EventArrivedEventArgs e)
    {
        var processName = e.NewEvent["ProcessName"]?.ToString();

        if (string.IsNullOrWhiteSpace(processName))
            return;

        var processId = TryReadProcessId(e);

        var onStarted = _onStarted;

        if (onStarted is null)
            return;

        _ = NotifyProcessStartedAsync(
            onStarted,
            new ProcessInfo(processName, processId));
    }

    private static async Task NotifyProcessStartedAsync(
        Func<ProcessInfo, Task> onStarted,
        ProcessInfo process)
    {
        try
        {
            await onStarted(process);
        }
        catch
        {
            // ignored because a failed consumer should not crash the program
        }
    }

    private static int? TryReadProcessId(EventArrivedEventArgs e)
    {
        var rawProcessId = e.NewEvent["ProcessID"];

        if (rawProcessId is null)
            return null;

        try
        {
            return Convert.ToInt32(rawProcessId, CultureInfo.InvariantCulture);
        }
        catch
        {
            return null;
        }
    }

    private static int? TryGetProcessId(Process process)
    {
        try
        {
            return process.Id;
        }
        catch
        {
            return null;
        }
    }

    private void StopWatcher()
    {
        if (_watcher is null)
            return;

        try
        {
            _watcher.Stop();
        }
        catch
        {
            // ignore because shutdown errors does not affect the user experience
        }

        if (_eventHandler is not null)
        {
            _watcher.EventArrived -= _eventHandler;
            _eventHandler = null;
        }

        _watcher.Dispose();
        _watcher = null;
        _onStarted = null;
    }

    public void Dispose()
    {
        StopWatcher();
    }
}