namespace Contracts
{
    /// <summary>
    /// Component that observes one exam-integrity signal and emits monitor events.
    /// </summary>
    /// <remarks>
    /// A monitor detects and reports. It should not permanently change the machine state.
    /// For blocking or prevention, use an <see cref="IMitigator" /> instead.
    /// </remarks>
    public interface IMonitor : IDisposable
    {
        /// <summary>
        /// Name defined for the component.
        /// </summary>
        string Name { get; }

        /// <summary>
        /// Entry point function that starts monitoring until <paramref name="ct" /> is cancelled.
        /// </summary>
        /// <param name="onEvent">
        /// Callback used to publish each event detected by the monitor.
        /// Implementations should await it when event ordering matters.
        /// </param>
        /// <param name="ct">
        /// Cancellation token used by the service to stop the monitor during shutdown or exam end.
        /// </param>
        /// <returns>
        /// A task that completes when monitoring stops. Normal cancellation should not be treated as a failure.
        /// </returns>
        Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct);
    }
}