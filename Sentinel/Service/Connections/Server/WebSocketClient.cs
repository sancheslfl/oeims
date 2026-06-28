using Contracts;
using Contracts.Ipc;
using Contracts.WebSocket;
using System.Net;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace OEIMS.Sentinel.Service.Connections.Server;

/// <summary>
/// Maintains the realtime WebSocket connection with the server.
/// </summary>
/// <remarks>
/// Communication:
/// <code>
/// Windows Service <-> OEIMS Server
/// </code>
/// Sends monitor events and receives server commands.
/// </remarks>
internal sealed class WebSocketClient(
    ServerConfig config,
    ServerSession serverSession,
    AgentCommandPipeClient agentClient,
    ILogger<WebSocketClient> logger) : IAsyncDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    private readonly string _realtimeBaseUrl = config.RealtimeBaseUrl.TrimEnd('/');
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    private ClientWebSocket? _ws;

    public async Task StartAsync(CancellationToken ct)
    {
        logger.LogInformation("Waiting for authorization");

        while (!ct.IsCancellationRequested)
        {
            var authorization = await serverSession.WaitUntilAuthorizedAsync(ct);
            var authorizationChanged = serverSession.WaitUntilAuthorizationChangedAsync(ct);

            if (!serverSession.IsCurrent(authorization))
                continue;

            var uri = new Uri($"{_realtimeBaseUrl}/ws/daemon/{authorization.ParticipantId}");

            try
            {
                _ws?.Dispose();
                _ws = new ClientWebSocket();
                _ws.Options.CollectHttpResponseDetails = true;
                _ws.Options.SetRequestHeader("Authorization", $"Bearer {authorization.Token}");

                await _ws.ConnectAsync(uri, ct);

                logger.LogInformation("Connected to server as authorized participant");

                while (!ct.IsCancellationRequested && _ws.State == WebSocketState.Open)
                {
                    using var receiveCts = CancellationTokenSource.CreateLinkedTokenSource(ct);

                    var receiveTask = ReceiveTextAsync(_ws, receiveCts.Token);
                    var completedTask = await Task.WhenAny(receiveTask, authorizationChanged);

                    if (completedTask == authorizationChanged)
                    {
                        logger.LogInformation("Authorization changed; reconnecting to server");

                        receiveCts.Cancel();
                        _ws.Abort();

                        await IgnoreReceiveFailureAsync(receiveTask);
                        break;
                    }

                    var message = await receiveTask;

                    if (message is null)
                    {
                        logger.LogInformation("Server closed the connection");
                        break;
                    }

                    await HandleServerMessageAsync(message, ct);
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
                logger.LogDebug(ex, "Disconnected from server; retrying in 5 seconds");

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
                "Socket not open; event dropped: [{Monitor}] {Message}",
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
            logger.LogDebug(ex, "Event not sent because socket failed");
        }
        finally
        {
            _sendLock.Release();
        }
    }

    private async Task HandleServerMessageAsync(string json, CancellationToken ct)
    {
        ServerMessage? message;

        try
        {
            message = JsonSerializer.Deserialize<ServerMessage>(json, JsonOptions);
        }
        catch (JsonException ex)
        {
            logger.LogDebug(ex, "Ignored malformed server message");
            return;
        }

        if (message is null)
            return;

        switch (message.Type)
        {
            case ServerMessageTypes.ExamIdentityCode:
                await SendExamIdentityCodeAsync(message.Data, ct);
                break;

            default:
                logger.LogDebug("Ignored unknown server message type: {Type}", message.Type);
                break;
        }
    }

    private async Task SendExamIdentityCodeAsync(JsonElement data, CancellationToken ct)
    {
        if (data.ValueKind != JsonValueKind.String)
        {
            logger.LogWarning("Ignored exam identity code message with invalid data");
            return;
        }

        var code = data.GetString();

        if (string.IsNullOrWhiteSpace(code))
        {
            logger.LogWarning("Ignored empty exam identity code message");
            return;
        }

        await agentClient.SendAsync(new ShowExamIdentityCodeCommand(code), ct);
    }

    private static async Task<string?> ReceiveTextAsync(ClientWebSocket ws, CancellationToken ct)
    {
        var buffer = new byte[1024];
        using var message = new MemoryStream();

        while (true)
        {
            var result = await ws.ReceiveAsync(buffer, ct);

            if (result.MessageType == WebSocketMessageType.Close)
                return null;

            if (result.MessageType != WebSocketMessageType.Text)
                continue;

            message.Write(buffer, 0, result.Count);

            if (result.EndOfMessage)
                return Encoding.UTF8.GetString(message.ToArray());
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
                    "Service shutting down",
                    CancellationToken.None);
            }
            catch
            {
            }
        }
    }

    private static async Task IgnoreReceiveFailureAsync(Task<string?> receiveTask)
    {
        try
        {
            await receiveTask;
        }
        catch (OperationCanceledException)
        {
        }
        catch (WebSocketException)
        {
        }
    }

    public async ValueTask DisposeAsync()
    {
        await CloseAsync();
        _ws?.Dispose();
        _sendLock.Dispose();
    }
}