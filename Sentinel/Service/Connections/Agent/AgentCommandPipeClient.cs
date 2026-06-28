using Contracts.Ipc;
using System.IO.Pipes;
using System.Text.Json;
using System.Text.Json.Serialization;

/// <summary>
/// Sends commands through the command pipe from the Sentinel Service to the Sentinel Agent.
/// </summary>
/// <remarks>
/// Communication:
/// <code>
/// Sentinel Service -> Sentinel Agent
/// </code>
/// </remarks>
internal sealed class AgentCommandPipeClient(
    ILogger<AgentCommandPipeClient> logger) : IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        Converters =
        {
            new JsonStringEnumConverter()
        }
    };

    private readonly SemaphoreSlim _writeLock = new(1, 1);

    public async Task SendAsync(AgentCommand command, CancellationToken ct = default)
    {
        await _writeLock.WaitAsync(ct);

        try
        {
            await using var pipe = new NamedPipeClientStream(
                ".",
                PipeNames.AgentCommands,
                PipeDirection.Out,
                PipeOptions.Asynchronous);

            await pipe.ConnectAsync(timeout: 2_000, ct);

            await using var writer = new StreamWriter(pipe);

            var json = JsonSerializer.Serialize(
                command,
                command.GetType(),
                JsonOptions);

            await writer.WriteLineAsync(json.AsMemory(), ct);
            await writer.FlushAsync(ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            throw;
        }
        catch (TimeoutException)
        {
            logger.LogWarning("Agent unavailable; command not sent: {CommandType}", command.Type);
        }
        catch (IOException ex)
        {
            logger.LogWarning("Agent command pipe disconnected.");
            logger.LogDebug(ex, "Agent command pipe write failed.");
        }
        finally
        {
            _writeLock.Release();
        }
    }

    public void Dispose()
    {
        _writeLock.Dispose();
    }
}