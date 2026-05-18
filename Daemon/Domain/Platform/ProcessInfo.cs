namespace Daemon.Domain.Platform;

public sealed record ProcessInfo(
    string Name,
    int? ProcessId = null);