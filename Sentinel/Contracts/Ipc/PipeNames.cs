namespace Contracts.Ipc;

/// <summary>
/// Named pipe identifiers used by the Sentinel Service and Sentinel Agent.
/// </summary>
/// <remarks>
/// These names are the local IPC protocol endpoints. Both sides must use the same values.
/// Changing one of them without changing the other side breaks Service/Agent communication.
/// </remarks>
public static class PipeNames
{
    /// <summary>
    /// Pipe used by the Agent to send heartbeats and monitor events to the Service.
    /// Direction: Agent -> Service.
    /// </summary>
    public const string AgentEvents = "oeims.sentinel.agent.events";

    /// <summary>
    /// Pipe used by the Service to send commands to the Agent.
    /// Direction: Service -> Agent.
    /// </summary>
    public const string AgentCommands = "oeims.sentinel.agent.commands";
}