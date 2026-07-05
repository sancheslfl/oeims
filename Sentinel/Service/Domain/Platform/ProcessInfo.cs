namespace OEIMS.Sentinel.Service.Domain.Platform;

/// <summary>
/// Minimal process information needed by the Service process monitor.
/// </summary>
/// <param name="Name">
/// Process executable name as reported by the operating system or watcher.
/// It may include an extension such as <c>slack.exe</c>; the monitor normalizes it before matching.
/// </param>
/// <param name="ProcessId">
/// Optional operating system process id. Some sources may not provide it, so monitor logic must not depend on it.
/// </param>
public sealed record ProcessInfo(
    string Name,
    int? ProcessId = null);