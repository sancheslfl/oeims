namespace OEIMS.Sentinel.Service.Domain.Platform;

public sealed record ProcessInfo(
    string Name,
    int? ProcessId = null);