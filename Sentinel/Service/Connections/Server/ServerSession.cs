using System.Diagnostics.CodeAnalysis;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed record ServerAuthorization(
    string Token,
    string ParticipantId);

internal sealed class ServerSession
{
    private readonly Lock _lock = new();

    private readonly TaskCompletionSource _authorized =
        new(TaskCreationOptions.RunContinuationsAsynchronously);

    private ServerAuthorization? _authorization;

    public bool IsAuthorized => _authorized.Task.IsCompletedSuccessfully;

    public void Authorize(string token, string participantId)
    {
        if (string.IsNullOrWhiteSpace(token))
            throw new ArgumentException("Token cannot be empty.", nameof(token));

        if (string.IsNullOrWhiteSpace(participantId))
            throw new ArgumentException("Participant id cannot be empty.", nameof(participantId));

        lock (_lock)
        {
            _authorization = new ServerAuthorization(token, participantId);
            _authorized.TrySetResult();
        }
    }

    public async Task<ServerAuthorization> WaitUntilAuthorizedAsync(CancellationToken ct)
    {
        await _authorized.Task.WaitAsync(ct);
        return GetAuthorization();
    }

    public ServerAuthorization GetAuthorization()
    {
        lock (_lock)
        {
            return _authorization
                ?? throw new InvalidOperationException("Sentinel is not authorized.");
        }
    }

    public bool TryGetAuthorization(
        [NotNullWhen(true)] out ServerAuthorization? authorization)
    {
        lock (_lock)
        {
            authorization = _authorization;
            return authorization is not null;
        }
    }
}