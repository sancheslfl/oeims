namespace OEIMS.Sentinel.Service.Domain.Platform;

/// <summary>
/// Interface that abstracts process monitoring logic from the operating system process APIs.
/// </summary>
/// <remarks>
/// <see cref="OEIMS.Sentinel.Service.Monitors.ProcessMonitor" /> depends on this abstraction so its policy can be tested
/// without starting, watching, or killing real processes.
/// </remarks>
public interface IProcessSource : IDisposable
{
    /// <summary>
    /// Starts watching for newly started processes.
    /// </summary>
    /// <param name="onStarted">
    /// Callback invoked for each process start detected by the platform implementation.
    /// </param>
    /// <param name="ct">
    /// Cancellation token used to stop the watcher.
    /// </param>
    /// <returns>
    /// A task that completes when the watcher stops. Normal cancellation should not be treated as a failure.
    /// </returns>
    Task StartAsync(Func<ProcessInfo, Task> onStarted, CancellationToken ct);

    /// <summary>
    /// Attempts to kill all running processes matching <paramref name="processName" />.
    /// </summary>
    /// <param name="processName">
    /// Process name without path. The caller should pass the normalized executable name, for example <c>slack</c>.
    /// </param>
    /// <param name="ct">Cancellation token used to stop the operation.</param>
    /// <returns>
    /// One result per matching process instance. Failed items are returned as values when possible,
    /// so the monitor can report partial success instead of losing detail.
    /// </returns>
    Task<IReadOnlyList<ProcessKillResult>> KillByNameAsync(
        string processName,
        CancellationToken ct);
}