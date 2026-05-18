namespace Daemon.ServerConnection;

internal record ServerConfig
{
    public string BaseUrl { get; init; } = string.Empty;
    public string Token { get; init; } = string.Empty;
    public string ParticipantId { get; init; } = string.Empty;

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(BaseUrl) &&
        !string.IsNullOrWhiteSpace(Token) &&
        !string.IsNullOrWhiteSpace(ParticipantId);
}
