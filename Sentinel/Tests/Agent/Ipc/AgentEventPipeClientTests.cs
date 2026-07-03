using System.IO.Pipes;
using System.Text.Json;
using Contracts;
using Contracts.Ipc;
using Microsoft.Extensions.Logging.Abstractions;
using OEIMS.Sentinel.Agent.Ipc;
using Xunit;

namespace Tests.Agent.Ipc;

[Collection("Agent event pipe")]
public sealed class AgentEventPipeClientTests
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    [Fact(DisplayName = "Heartbeat messages are written to the service event pipe")]
    public async Task HeartbeatMessagesAreWrittenToTheServiceEventPipe()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var readTask = ReadOneMessageAsync(cts.Token);

        await using var client = new AgentEventPipeClient(
            NullLogger<AgentEventPipeClient>.Instance);

        await client.SendHeartbeatAsync(cts.Token);

        var message = await readTask;

        Assert.Equal(AgentMessageType.Heartbeat, message.Type);
        Assert.Null(message.Event);
    }

    [Fact(DisplayName = "Monitor events are written to the service event pipe")]
    public async Task MonitorEventsAreWrittenToTheServiceEventPipe()
    {
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var readTask = ReadOneMessageAsync(cts.Token);

        await using var client = new AgentEventPipeClient(
            NullLogger<AgentEventPipeClient>.Instance);

        var monitorEvent = new MonitorEvent(
            "FocusMonitor",
            "Focus lost: Code Editor",
            Severity.Warning);

        await client.SendEventAsync(monitorEvent, cts.Token);

        var message = await readTask;

        Assert.Equal(AgentMessageType.Event, message.Type);
        Assert.Equal(monitorEvent, message.Event);
    }

    private static async Task<AgentPipeMessage> ReadOneMessageAsync(CancellationToken ct)
    {
        await using var pipe = new NamedPipeServerStream(
            PipeNames.AgentEvents,
            PipeDirection.In,
            maxNumberOfServerInstances: 1,
            PipeTransmissionMode.Byte,
            PipeOptions.Asynchronous);

        await pipe.WaitForConnectionAsync(ct);

        using var reader = new StreamReader(pipe);
        var line = await reader.ReadLineAsync(ct);

        Assert.False(string.IsNullOrWhiteSpace(line));

        var message = JsonSerializer.Deserialize<AgentPipeMessage>(line, JsonOptions);

        return Assert.IsType<AgentPipeMessage>(message);
    }
}

[CollectionDefinition("Agent event pipe", DisableParallelization = true)]
public sealed class AgentEventPipeCollection;
