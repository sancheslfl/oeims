namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class ServerConfig
{
    public bool Enabled { get; init; } = true;

    public string ApiBaseUrl { get; init; } = "";
    public string RealtimeBaseUrl { get; init; } = "";

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(ApiBaseUrl) &&
        !string.IsNullOrWhiteSpace(RealtimeBaseUrl);

    public bool ShouldConnect => Enabled && IsConfigured;
}