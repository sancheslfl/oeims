namespace OEIMS.Sentinel.Service.Domain.Platform;

/// <summary>
/// Result of one attempt to kill one process instance.
/// </summary>
/// <param name="ProcessName">
/// Display name of the process instance that was targeted.
/// </param>
/// <param name="ProcessId">
/// Optional operating system process id of the targeted instance.
/// </param>
/// <param name="Succeeded">
/// Whether the process instance was successfully killed.
/// </param>
/// <param name="ErrorMessage">
/// Failure reason when <paramref name="Succeeded" /> is <c>false</c>. Keep it short because it can be included in a monitor event.
/// </param>
public sealed record ProcessKillResult(
    string ProcessName,
    int? ProcessId,
    bool Succeeded,
    string? ErrorMessage = null);