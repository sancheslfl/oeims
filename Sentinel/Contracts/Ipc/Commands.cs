namespace Contracts.Ipc
{
    public enum AgentCommandType
    {
        ShowExamIdentityCode
    }

    /// <summary>
    /// Commands sent by the Sentinel Service to the Sentinel Agent
    /// </summary>
    public abstract record AgentCommand(AgentCommandType Type);

    /// <summary>
    /// Command to display the exam identity code to the student.
    /// </summary>
    public sealed record ShowExamIdentityCodeCommand(string Code)
        : AgentCommand(AgentCommandType.ShowExamIdentityCode);
}
