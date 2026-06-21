using System.IO.Pipes;
using System.Text.Json;
using Contracts;
using Contracts.Ipc;

namespace OEIMS.Sentinel.Agent.Ipc;

internal sealed class AgentPipeClient(
    ILogger<AgentPipeClient> logger
) : IAsyncDisposable
{
    private readonly SemaphoreSlim _writeLock = new(1, 1);
    private NamedPipeClientStream? _pipe;
    private StreamWriter? _writer;

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task SendHeartbeatAsync(CancellationToken ct)
    {
        await SendAsync(AgentPipeMessage.Heartbeat(), ct);
    }

    public async Task SendEventAsync(MonitorEvent e, CancellationToken ct)
    {
        await SendAsync(AgentPipeMessage.FromEvent(e), ct);
    }

    private async Task SendAsync(AgentPipeMessage message, CancellationToken ct)
    {
        await _writeLock.WaitAsync(ct);

        try
        {
            await ConnectPipeAsync(ct);

            var json = JsonSerializer.Serialize(message, JsonOptions);

            await _writer!.WriteLineAsync(json);
            await _writer.FlushAsync(ct);
        }
        catch (IOException ex)
        {
            logger.LogWarning(ex, "Agent pipe disconnected.");

            DisposeConnection();
        }
        catch (TimeoutException ex)
        {
            logger.LogWarning(ex, "Agent pipe connection timed out.");

            DisposeConnection();
        }
        finally
        {
            _writeLock.Release();
        }
    }

    private async Task ConnectPipeAsync(CancellationToken ct)
    {
        if (_pipe?.IsConnected == true && _writer is not null)
            return;

        DisposeConnection();

        _pipe = new NamedPipeClientStream(
            ".",
            SentinelPipeNames.Agent,
            PipeDirection.Out,
            PipeOptions.Asynchronous);

        logger.LogInformation("Connecting to Service pipe...");

        await _pipe.ConnectAsync(timeout: 2_000, ct);

        _writer = new StreamWriter(_pipe)
        {
            AutoFlush = true
        };

        logger.LogInformation("Connected to Service pipe.");
    }

    private void DisposeConnection()
    {
        _writer?.Dispose();
        _pipe?.Dispose();

        _writer = null;
        _pipe = null;
    }

    public async ValueTask DisposeAsync()
    {
        await _writeLock.WaitAsync();

        try
        {
            DisposeConnection();
        }
        finally
        {
            _writeLock.Release();
            _writeLock.Dispose();
        }
    }
}