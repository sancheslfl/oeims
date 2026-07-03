namespace Contracts
{
    /// <summary>
    /// Severity level attached to a monitor event.
    /// </summary>
    public enum Severity
    {
        /// <summary>
        /// Normal information that helps explain the current state, such as a successful baseline initialization.
        /// </summary>
        Info,

        /// <summary>
        /// Suspicious or unexpected behavior that should be visible to the professor but does not stop the system.
        /// </summary>
        Warning,

        /// <summary>
        /// Serious failure where the monitoring flow cannot be trusted or continued safely.
        /// </summary>
        Critical
    }
}