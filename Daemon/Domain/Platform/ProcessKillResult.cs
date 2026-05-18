namespace Daemon.Domain.Platform;

public sealed record ProcessKillResult(
    string ProcessName,
    int? ProcessId,
    bool Succeeded,
    string? ErrorMessage = null);