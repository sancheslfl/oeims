using System.Collections.Concurrent;
using Contracts;
using OEIMS.Sentinel.Agent.Domain;
using OEIMS.Sentinel.Agent.Monitors;
using Xunit;

namespace OEIMS.Sentinel.Agent.Tests.Monitors;

public sealed class FocusMonitorTests
{
    [Fact(DisplayName = "Focus outside the exam window emits a warning")]
    public async Task FocusOutsideTheExamWindowEmitsAWarning()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        var events = new ConcurrentQueue<MonitorEvent>();

        await monitor.StartAsync(AddTo(events), CancellationToken.None);
        await source.RaiseAsync("Code Editor");

        var monitorEvent = Assert.Single(events);
        Assert.Equal(nameof(FocusMonitor), monitorEvent.MonitorName);
        Assert.Equal("Focus lost: Code Editor", monitorEvent.Message);
        Assert.Equal(Severity.Warning, monitorEvent.Severity);
    }

    [Fact(DisplayName = "Focus on the exam window does not emit an event")]
    public async Task FocusOnTheExamWindowDoesNotEmitAnEvent()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        var events = new ConcurrentQueue<MonitorEvent>();

        await monitor.StartAsync(AddTo(events), CancellationToken.None);
        await source.RaiseAsync("OEIMS Exam - Student");

        Assert.Empty(events);
    }

    [Fact(DisplayName = "Repeated focus on the same window is reported only once")]
    public async Task RepeatedFocusOnTheSameWindowIsReportedOnlyOnce()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        var events = new ConcurrentQueue<MonitorEvent>();

        await monitor.StartAsync(AddTo(events), CancellationToken.None);
        await source.RaiseAsync("Chat App");
        await source.RaiseAsync("Chat App");

        Assert.Single(events);
    }

    [Fact(DisplayName = "A different external window is reported after the first one")]
    public async Task ADifferentExternalWindowIsReportedAfterTheFirstOne()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        var events = new ConcurrentQueue<MonitorEvent>();

        await monitor.StartAsync(AddTo(events), CancellationToken.None);
        await source.RaiseAsync("Chat App");
        await source.RaiseAsync("File Explorer");

        Assert.Equal(new[] { "Focus lost: Chat App", "Focus lost: File Explorer" }, events.Select(e => e.Message));
    }

    [Fact(DisplayName = "Returning to the same external window after the exam window is reported again")]
    public async Task ReturningToTheSameExternalWindowAfterTheExamWindowIsReportedAgain()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        var events = new ConcurrentQueue<MonitorEvent>();

        await monitor.StartAsync(AddTo(events), CancellationToken.None);
        await source.RaiseAsync("Chat App");
        await source.RaiseAsync("OEIMS Exam - Student");
        await source.RaiseAsync("Chat App");

        Assert.Equal(new[] { "Focus lost: Chat App", "Focus lost: Chat App" }, events.Select(e => e.Message));
    }

    [Fact(DisplayName = "Focus monitor passes the cancellation token to the active window source")]
    public async Task FocusMonitorPassesTheCancellationTokenToTheActiveWindowSource()
    {
        var source = new FakeActiveWindowSource();
        using var monitor = new FocusMonitor(source);
        using var cts = new CancellationTokenSource();

        await monitor.StartAsync(_ => Task.CompletedTask, cts.Token);

        Assert.Equal(cts.Token, source.ReceivedCancellationToken);
    }

    [Fact(DisplayName = "Disposing the focus monitor disposes the active window source")]
    public void DisposingTheFocusMonitorDisposesTheActiveWindowSource()
    {
        var source = new FakeActiveWindowSource();
        var monitor = new FocusMonitor(source);

        monitor.Dispose();

        Assert.True(source.Disposed);
    }

    private static Func<MonitorEvent, Task> AddTo(ConcurrentQueue<MonitorEvent> events) => e =>
    {
        events.Enqueue(e);
        return Task.CompletedTask;
    };

    private sealed class FakeActiveWindowSource : IActiveWindowSource
    {
        private Func<ActiveWindowInfo, Task>? _onChanged;

        public bool Disposed { get; private set; }
        public CancellationToken ReceivedCancellationToken { get; private set; }

        public Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct)
        {
            _onChanged = onChanged;
            ReceivedCancellationToken = ct;
            return Task.CompletedTask;
        }

        public Task RaiseAsync(string title) => _onChanged?.Invoke(new ActiveWindowInfo(title)) ?? Task.CompletedTask;

        public void Dispose()
        {
            Disposed = true;
        }
    }
}
