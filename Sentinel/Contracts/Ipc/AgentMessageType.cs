namespace Contracts.Ipc;

/// <summary>
/// Kind of message sent by the Sentinel Agent to the Sentinel Service.
/// </summary>
public enum AgentMessageType
{
    /// <summary>
    /// Lightweight liveness signal. It proves the Agent process is still connected to the Service.
    /// </summary>
    Heartbeat,

    /// <summary>
    /// Monitor event detected by the Agent, for example focus leaving the exam window.
    /// </summary>
    Event
}