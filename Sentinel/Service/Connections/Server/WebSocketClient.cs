using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using Contracts;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class WebSocketClient(
    ServerConfig config,
    ServerSession serverSession,
    ILogger<WebSocketClient> logger) : IAsyncDisposable
{
    private readonly string _realtimeBaseUrl = config.RealtimeBaseUrl.TrimEnd('/');
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    private ClientWebSocket? _ws;

    public async Task StartAsync(CancellationToken ct)
    {
        logger.LogInformation("Waiting for Sentinel authorization");

        while (!ct.IsCancellationRequested)
        {
            var authorization = await serverSession.WaitUntilAuthorizedAsync(ct);
            var uri = new Uri($"{_realtimeBaseUrl}/ws/daemon/{authorization.ParticipantId}");

            try
            {
                _ws?.Dispose();
                _ws = new ClientWebSocket();
                _ws.Options.CollectHttpResponseDetails = true;
                _ws.Options.SetRequestHeader("Authorization", $"Bearer {authorization.Token}");

                await _ws.ConnectAsync(uri, ct);

                logger.LogInformation("Connected to {Uri}", uri);

                var buffer = new byte[256];

                while (!ct.IsCancellationRequested && _ws.State == WebSocketState.Open)
                {
                    var result = await _ws.ReceiveAsync(buffer, ct);

                    if (result.MessageType == WebSocketMessageType.Close)
                    {
                        logger.LogWarning(
                            "Server closed the connection: {Reason}",
                            result.CloseStatusDescription);

                        break;
                    }
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (WebSocketException ex) when (IsAuthorizationRejected(_ws))
            {
                serverSession.Clear();
                logger.LogWarning(ex, "Authorization rejected by server");
            }
            catch (Exception ex)
            {
                logger.LogDebug(ex, "Disconnected and retrying in 5 s");

                try { await Task.Delay(TimeSpan.FromSeconds(5), ct); }
                catch (OperationCanceledException) { break; }
            }
        }

        await CloseAsync();
    }

    public async Task SendEventAsync(MonitorEvent e, CancellationToken ct = default)
    {
        if (_ws?.State != WebSocketState.Open)
        {
            logger.LogDebug(
                "Socket not open and event dropped: [{Monitor}] {Message}",
                e.MonitorName,
                e.Message);

            return;
        }

        var payload = JsonSerializer.Serialize(new
        {
            monitorName = e.MonitorName,
            message = e.Message,
            severity = e.Severity.ToString()
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
        catch (WebSocketException ex)
        {
            _ws.Abort();
            logger.LogDebug(ex, "[ServerConnection] Event not sent because socket failed");
        }
        finally
        {
            _sendLock.Release();
        }
    }

    private static bool IsAuthorizationRejected(ClientWebSocket? webSocket) =>
        webSocket?.HttpStatusCode is
            HttpStatusCode.Unauthorized or
            HttpStatusCode.Forbidden;

    private async Task CloseAsync()
    {
        if (_ws is { State: WebSocketState.Open })
        {
            try
            {
                await _ws.CloseAsync(
                    WebSocketCloseStatus.NormalClosure,
                    "Sentinel shutting down",
                    CancellationToken.None);
            }
            catch
            {
                
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        await CloseAsync();
        _ws?.Dispose();
        _sendLock.Dispose();
    }
}