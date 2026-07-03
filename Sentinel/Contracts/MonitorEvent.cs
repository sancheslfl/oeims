namespace Contracts
{
    /// <summary>
    /// Event emitted by a Sentinel monitor when it observes something relevant during an exam.
    /// </summary>
    /// <param name="MonitorName">
    /// Name of the monitor that produced the event, for example <c>NetworkMonitor</c> or <c>ProcessMonitor</c>.
    /// The server and UI use this to group events by source.
    /// </param>
    /// <param name="Message">
    /// Comprehensive explanation of what happened. Keep this clear enough for a professor to understand without reading logs.
    /// </param>
    /// <param name="Severity">
    /// Importance of the event. Use <see cref="Severity.Info" /> for normal state changes,
    /// <see cref="Severity.Warning" /> for suspicious or recoverable states, and
    /// <see cref="Severity.Critical" /> only when the exam cannot continue safely.
    /// </param>
    public record MonitorEvent(string MonitorName, string Message, Severity Severity);
}