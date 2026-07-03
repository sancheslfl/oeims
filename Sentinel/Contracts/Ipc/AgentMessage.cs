namespace Contracts.Ipc;

/// <summary>
/// Message sent through the Agent event pipe.
/// </summary>
/// <param name="Type">
/// Message kind. The Service uses this to decide whether the payload is a heartbeat or a monitor event.
/// </param>
/// <param name="SentAt">
/// UTC timestamp created by the Agent when the message is sent.
/// </param>
/// <param name="Event">
/// Monitor event payload. This is present only when <paramref name="Type" /> is <see cref="AgentMessageType.Event" />.
/// </param>
public sealed record AgentPipeMessage(
    AgentMessageType Type,
    DateTimeOffset SentAt,
    MonitorEvent? Event)
{
    /// <summary>
    /// Creates a heartbeat message for the Service.
    /// </summary>
    /// <returns>A message with type <see cref="AgentMessageType.Heartbeat" /> and no event payload.</returns>
    public static AgentPipeMessage Heartbeat() =>
        new(
            AgentMessageType.Heartbeat,
            DateTimeOffset.UtcNow,
            Event: null);

    /// <summary>
    /// Wraps an Agent monitor event so it can be sent to the Service.
    /// </summary>
    /// <param name="e">Event detected by an Agent-side monitor.</param>
    /// <returns>A message with type <see cref="AgentMessageType.Event" /> and the supplied event payload.</returns>
    public static AgentPipeMessage FromEvent(MonitorEvent e) =>
        new(
            AgentMessageType.Event,
            DateTimeOffset.UtcNow,
            Event: e);
}