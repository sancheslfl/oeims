using System.IO.Pipes;
using System.Text.Json;
using System.Text.Json.Serialization;
using Contracts.Ipc;

namespace OEIMS.Sentinel.Agent.Ipc;

/// <summary>
/// Exposes the command pipe used by the Sentinel Service to send commands to the Sentinel Agent.
/// </summary>
/// <remarks>
/// Communication:
/// <code>
/// Sentinel Service -> Sentinel Agent
/// </code>
/// </remarks>
internal sealed class AgentCommandPipeServer(
    ILogger<AgentCommandPipeServer> logger)
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        Converters =
        {
            new JsonStringEnumConverter()
        }
    };

    public async Task StartAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            await using var pipe = new NamedPipeServerStream(
                PipeNames.AgentCommands,
                PipeDirection.In,
                maxNumberOfServerInstances: 1,
                PipeTransmissionMode.Byte,
                PipeOptions.Asynchronous);

            try
            {
                await pipe.WaitForConnectionAsync(ct);

                using var reader = new StreamReader(pipe);

                var json = await reader.ReadLineAsync(ct);

                if (string.IsNullOrWhiteSpace(json))
                    continue;

                await HandleCommandAsync(json, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                break;
            }
            catch (IOException ex)
            {
                logger.LogDebug(ex, "Agent command pipe disconnected.");
            }
            catch (JsonException ex)
            {
                logger.LogWarning(ex, "Ignored malformed Agent command.");
            }
        }
    }

    private Task HandleCommandAsync(string json, CancellationToken ct)
    {
        using var document = JsonDocument.Parse(json);

        if (!document.RootElement.TryGetProperty("type", out var type))
            return Task.CompletedTask;

        return type.Deserialize<AgentCommandType>(JsonOptions) switch
        {
            AgentCommandType.ShowExamIdentityCode =>
                HandleShowExamIdentityCodeAsync(json, ct),

            _ => Task.CompletedTask
        };
    }

    private Task HandleShowExamIdentityCodeAsync(string json, CancellationToken ct)
    {
        var command = JsonSerializer.Deserialize<ShowExamIdentityCodeCommand>(
            json,
            JsonOptions);

        if (command is null)
            return Task.CompletedTask;

        // TODO: call overlay here
        // overlay.ShowExamIdentityCode(command.Code);

        return Task.CompletedTask;
    }
}