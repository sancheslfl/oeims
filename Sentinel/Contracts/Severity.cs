namespace Contracts
{
    /// <summary>
    /// Severity level attached to a monitor event.
    /// </summary>
    public enum Severity
    {
        /// <summary>
        /// Normal information that helps explain the current state.
        /// </summary>
        Info,

        /// <summary>
        /// Suspicious behavior that should be visible to the professor but does not stop the system.
        /// </summary>
        Warning,

        /// <summary>
        /// Integrity offense where the exam cannot continue safely and the incident invalidates the exam flow.
        /// </summary>
        Critical
    }
}