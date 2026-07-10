using Contracts;
using OEIMS.Sentinel.Agent.Domain;

namespace OEIMS.Sentinel.Agent.Monitors;

/// <summary>
/// Detects when the student moves focus away from the exam window.
/// </summary>
/// <remarks>
/// Focus loss is reported as a warning because it is suspicious, but it does not automatically prove misconduct.
/// Repeated notifications for the same window title are ignored to avoid flooding.
/// </remarks>
/// <param name="activeWindowSource">
/// Platform implementation that offers implementation for current foreground window interaction.
/// </param>
internal sealed class FocusMonitor(IActiveWindowSource activeWindowSource) : IMonitor
{
    /// <summary>
    /// Name used in monitor events and logs.
    /// </summary>
    public string Name => nameof(FocusMonitor);

    private const string ExamWindowTitle = "OEIMS Exam";

    private string _lastTitle = string.Empty;

    /// <summary>
    /// Starts listening for active-window changes until cancellation.
    /// </summary>
    /// <param name="onEvent">Callback used to publish focus-loss events.</param>
    /// <param name="ct">Cancellation token used when the Agent stops.</param>
    /// <returns>A task that completes when the active-window source stops.</returns>
    public Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
    {
        return activeWindowSource.StartAsync(async activeWindow =>
        {
            var title = activeWindow.Title;

            if (title == _lastTitle)
                return;

            _lastTitle = title;

            if (!title.Contains(ExamWindowTitle, StringComparison.Ordinal))
            {
                await onEvent(new MonitorEvent(
                    Name,
                    $"Focus lost: {title}",
                    Severity.Warning));
            }
        }, ct);
    }

    /// <summary>
    /// Releases the platform active-window source.
    /// </summary>
    public void Dispose()
    {
        activeWindowSource.Dispose();
    }
}