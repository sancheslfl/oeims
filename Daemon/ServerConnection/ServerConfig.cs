namespace Daemon.ServerConnection;

internal record ServerConfig
{
    public string BaseUrl { get; init; } = string.Empty;
    public string Token { get; init; } = string.Empty;
    public string ParticipantId { get; init; } = string.Empty;

    /// <summary>
    /// True when all three fields are present.
    /// If false the daemon runs in standalone mode with no server communication.
    /// </summary>
    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(BaseUrl) &&
        !string.IsNullOrWhiteSpace(Token) &&
        !string.IsNullOrWhiteSpace(ParticipantId);
}
