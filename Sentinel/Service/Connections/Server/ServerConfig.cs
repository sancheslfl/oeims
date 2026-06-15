namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class ServerConfig
{
    public bool Enabled { get; init; } = true;

    public string ApiBaseUrl { get; init; } = "";
    public string RealtimeBaseUrl { get; init; } = "";
    public string Token { get; init; } = "";
    public string ParticipantId { get; init; } = "";

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(ApiBaseUrl) &&
        !string.IsNullOrWhiteSpace(RealtimeBaseUrl) &&
        !string.IsNullOrWhiteSpace(Token) &&
        !string.IsNullOrWhiteSpace(ParticipantId);

    public bool ShouldConnect => Enabled && IsConfigured;
}