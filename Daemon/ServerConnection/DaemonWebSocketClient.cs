using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Daemon.Domain;

namespace Daemon.ServerConnection;

/// <summary>
/// Maintains a persistent WebSocket connection to /ws/daemon/{participantId}.
/// Forwards monitor events as JSON text frames and reconnects automatically on drop.
/// </summary>
internal sealed class DaemonWebSocketClient(
    ServerConfig config,
    ILogger<DaemonWebSocketClient> logger) : IAsyncDisposable
{
    private ClientWebSocket? _ws;
    private readonly SemaphoreSlim _sendLock = new(1, 1);
    private readonly Uri _uri = new(
        $"{config.BaseUrl.TrimEnd('/').Replace("http://", "ws://").Replace("https://", "wss://")}/ws/daemon/{config.ParticipantId}");

    /// <summary>
    /// Connects and keeps the connection alive until cancellation.
    /// Run this as a background task — it returns only when the token is cancelled.
    /// </summary>
    public async Task RunAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                _ws?.Dispose();
                _ws = new ClientWebSocket();
                _ws.Options.SetRequestHeader("Authorization", $"Bearer {config.Token}");

                await _ws.ConnectAsync(_uri, ct);
                logger.LogInformation("[ServerConnection] Connected to {Uri}", _uri);

                // Read loop — the server never sends frames to the daemon channel,
                // but we must read to detect server-side close frames.
                var buffer = new byte[256];
                while (!ct.IsCancellationRequested && _ws.State == WebSocketState.Open)
                {
                    var result = await _ws.ReceiveAsync(buffer, ct);
                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        logger.LogWarning("[ServerConnection] Server closed the connection: {Reason}",
                            result.CloseStatusDescription);
                        break;
                    }
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (Exception ex)
            {
                logger.LogWarning(ex, "[ServerConnection] Disconnected — retrying in 5 s");
                try { await Task.Delay(TimeSpan.FromSeconds(5), ct); }
                catch (OperationCanceledException) { break; }
            }
        }

        await CloseAsync();
    }

    /// <summary>
    /// Serialises a monitor event and sends it as a single text frame.
    /// Drops the frame (with a warning) if the socket is not open yet.
    /// Thread-safe — multiple monitors can call this concurrently.
    /// </summary>
    public async Task SendEventAsync(MonitorEvent e, CancellationToken ct = default)
    {
        if (_ws?.State != WebSocketState.Open)
        {
            logger.LogDebug("[ServerConnection] Socket not open — event dropped: [{Monitor}] {Message}",
                e.MonitorName, e.Message);
            return;
        }

        // Severity.ToString() → "Info" | "Warning" | "Critical"
        // Matches the server's toDomainSeverity() mapper exactly.
        var payload = JsonSerializer.Serialize(new
        {
            monitorName = e.MonitorName,
            message     = e.Message,
            severity    = e.Severity.ToString()
        });

        var bytes = Encoding.UTF8.GetBytes(payload);

        await _sendLock.WaitAsync(ct);
        try
        {
            await _ws.SendAsync(
                new ArraySegment<byte>(bytes),
                WebSocketMessageType.Text,
                endOfMessage: true,
                cancellationToken: ct);
        }
        finally
        {
            _sendLock.Release();
        }
    }

    private async Task CloseAsync()
    {
        if (_ws is { State: WebSocketState.Open })
        {
            try
            {
                await _ws.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Daemon shutting down",
                    CancellationToken.None);
            }
            catch { /* best-effort */ }
        }
    }

    public async ValueTask DisposeAsync()
    {
        await CloseAsync();
        _ws?.Dispose();
        _sendLock.Dispose();
    }
}
