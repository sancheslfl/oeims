namespace Contracts.Ipc;

public sealed record AgentPipeMessage(
    AgentMessageType Type,
    DateTimeOffset SentAt,
    MonitorEvent? Event)
{
    public static AgentPipeMessage Heartbeat() =>
        new(
            AgentMessageType.Heartbeat,
            DateTimeOffset.UtcNow,
            Event: null);

    public static AgentPipeMessage FromEvent(MonitorEvent e) =>
        new(
            AgentMessageType.Event,
            DateTimeOffset.UtcNow,
            Event: e);
}