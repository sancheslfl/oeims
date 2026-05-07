namespace Daemon.Domain;

public sealed record ProcessInfo(
    string Name,
    int? ProcessId = null);