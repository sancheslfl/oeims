namespace Daemon.ServerConnection;

internal sealed class ServerConfig
{
    public bool Enabled { get; init; } = true;

    public string BaseUrl { get; init; } = "";
    public string Token { get; init; } = "";
    public string ParticipantId { get; init; } = "";

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(BaseUrl) &&
        !string.IsNullOrWhiteSpace(Token) &&
        !string.IsNullOrWhiteSpace(ParticipantId);

    public bool ShouldConnect => Enabled && IsConfigured;
}