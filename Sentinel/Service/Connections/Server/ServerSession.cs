using System.Diagnostics.CodeAnalysis;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed record ServerAuthorization(
    string Token,
    string ParticipantId);

/**   <summary>
 *    Stores the current server authorization for the Sentinel Service.
 *    </summary>
 *    <remarks>
 *    The Sentinel receives its server token after the student verifies the join email.
 *    This class keeps that token in memory while the service is running and also persists it
 *    encrypted on disk so heartbeat and WebSocket communication can resume after a service restart.
 *   </remarks>
 */
internal sealed class ServerSession
{
    private static readonly string DefaultFilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
        "OEIMS",
        "Sentinel",
        "server-session.bin");

    private readonly Lock _lock = new();
    private readonly ILogger<ServerSession> _logger;
    private readonly string _filePath;

    private TaskCompletionSource _authorized = NewSignal();
    private TaskCompletionSource _authorizationChanged = NewSignal();

    private ServerAuthorization? _authorization;

    public ServerSession(ILogger<ServerSession> logger)
        : this(logger, DefaultFilePath)
    {
    }

    internal ServerSession(ILogger<ServerSession> logger, string filePath)
    {
        _logger = logger;
        _filePath = filePath;
        Load();
    }

    public bool IsAuthorized
    {
        get
        {
            lock (_lock)
                return _authorization is not null;
        }
    }

    public void Authorize(string token, string participantId)
    {
        if (string.IsNullOrWhiteSpace(token))
            throw new ArgumentException("Token cannot be empty.", nameof(token));

        if (string.IsNullOrWhiteSpace(participantId))
            throw new ArgumentException("Participant id cannot be empty.", nameof(participantId));

        lock (_lock)
        {
            var authorization = new ServerAuthorization(token, participantId);

            if (_authorization == authorization)
                return;

            _authorization = authorization;
            Save(_authorization);

            _authorized.TrySetResult();

            _authorizationChanged.TrySetResult();
            _authorizationChanged = NewSignal();
        }
    }

    public void Clear()
    {
        lock (_lock)
        {
            if (_authorization is null)
                return;

            _authorization = null;
            _authorized = NewSignal();

            _authorizationChanged.TrySetResult();
            _authorizationChanged = NewSignal();

            if (File.Exists(_filePath))
                File.Delete(_filePath);
        }
    }

    public async Task<ServerAuthorization> WaitUntilAuthorizedAsync(CancellationToken ct)
    {
        await _authorized.Task.WaitAsync(ct);
        return GetAuthorization();
    }

    public Task WaitUntilAuthorizationChangedAsync(CancellationToken ct)
    {
        lock (_lock)
            return _authorizationChanged.Task.WaitAsync(ct);
    }

    public bool IsCurrent(ServerAuthorization authorization)
    {
        lock (_lock)
            return _authorization == authorization;
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

    private void Load()
    {
        if (!File.Exists(_filePath))
            return;

        try
        {
            var encrypted = File.ReadAllBytes(_filePath);
            var bytes = ProtectedData.Unprotect(
                encrypted,
                null,
                DataProtectionScope.LocalMachine);

            var authorization = JsonSerializer.Deserialize<ServerAuthorization>(
                Encoding.UTF8.GetString(bytes));

            if (authorization is null)
                return;

            _authorization = authorization;
            _authorized.TrySetResult();

            _logger.LogDebug(
                "[ServerSession] Restored persisted authorization for participant {ParticipantId}",
                authorization.ParticipantId);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(
                ex,
                "Could not restore persisted authorization");

            Clear();
        }
    }

    /**
     *   <summary>
     *   Saves the Sentinel authorization data to disk using Windows DPAPI.
     *   </summary>
     *   <remarks>
     *   Data is encrypted without storing cryptographic key and stored in a file which lets 
     *   the Windows Service retrieve it after the service is restarted.
     *   </remarks>
     *   <param name="authorization">
     *   The authorization data to persist.
     *   </param>
     */
    private void Save(ServerAuthorization authorization)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_filePath)!);

        var bytes = Encoding.UTF8.GetBytes(
            JsonSerializer.Serialize(authorization));

        var encrypted = ProtectedData.Protect(
            bytes,
            null,
            DataProtectionScope.LocalMachine);  // the same machine can decrypt it, less fragile for a Windows Service

        File.WriteAllBytes(_filePath, encrypted);
    }

    private static TaskCompletionSource NewSignal() =>
        new(TaskCreationOptions.RunContinuationsAsynchronously);
}