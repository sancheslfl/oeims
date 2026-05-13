namespace Daemon.Domain
{
    // TODO: Add depth in monitor events (more information)
    public record MonitorEvent(string MonitorName, string Message, Severity Severity);
}
