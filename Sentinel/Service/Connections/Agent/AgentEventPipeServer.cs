using System.IO.Pipes;
using System.Text.Json;
using Contracts.Ipc;

namespace OEIMS.Sentinel.Service.Connections.Agent;

/// <summary>
/// Exposes the event pipe used by the Sentinel Agent to send activity events to the Sentinel Service.
/// </summary>
/// <remarks>
/// Communication:
/// <code>
/// Sentinel Service -> Sentinel Agent
/// </code>
/// </remarks>
internal sealed class AgentEventPipeServer(
    ILogger<AgentEventPipeServer> logger
)
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task StartAsync(
        Func<AgentPipeMessage, Task> onMessage,
        CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            await using var pipe = new NamedPipeServerStream(
                PipeNames.AgentEvents,
                PipeDirection.In,
                maxNumberOfServerInstances: 1,
                PipeTransmissionMode.Byte,
                PipeOptions.Asynchronous);

            logger.LogInformation("Waiting for Agent pipe connection...");

            try
            {
                await pipe.WaitForConnectionAsync(ct);

                logger.LogInformation("Agent connected.");

                await ReadMessagesAsync(pipe, onMessage, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                logger.LogInformation("Agent pipe server cancelled.");
            }
            catch (Exception ex)
            {
                logger.LogWarning(ex, "Agent pipe connection failed.");
            }
        }
    }

    private async Task ReadMessagesAsync(
        PipeStream pipe,
        Func<AgentPipeMessage, Task> onMessage,
        CancellationToken ct)
    {
        using var reader = new StreamReader(pipe);

        while (!ct.IsCancellationRequested && pipe.IsConnected)
        {
            var line = await reader.ReadLineAsync(ct);

            if (line is null)
                break;

            AgentPipeMessage? message;

            try
            {
                message = JsonSerializer.Deserialize<AgentPipeMessage>(line, JsonOptions);
            }
            catch (JsonException ex)
            {
                logger.LogWarning(ex, "Invalid Agent pipe message ignored.");
                continue;
            }

            if (message is null)
                continue;

            await onMessage(message);
        }

        logger.LogWarning("Agent disconnected.");
    }
}