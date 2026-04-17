namespace Daemon.Abstractions
{
    public record MonitorEvent(string MonitorName, string Message, Severity Severity, MonitorSignal Signal = MonitorSignal.None);
}
