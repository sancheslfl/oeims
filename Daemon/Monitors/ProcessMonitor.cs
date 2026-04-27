using System.Diagnostics;
using System.Management;
using Daemon.Abstractions;

namespace Daemon.Mitigators
{
    internal enum ProcessEvent
    {
        ForbiddenProcessKilled,
        ForbiddenProcessKillFailed
    }

    internal class ProcessMonitor : IMonitor
    {
        public string Name => nameof(ProcessMonitor);

        private readonly ManagementEventWatcher? _watcher;

        private readonly string[] _forbiddenProcesses =
        [
            "slack"
        ];

        public event Action<ProcessEvent, string>? ProcessViolationDetected;

        internal ProcessMonitor()
        {
            _watcher = new ManagementEventWatcher(
                new WqlEventQuery("SELECT * FROM Win32_ProcessStartTrace"));

            _watcher.EventArrived += OnProcessStarted;
        }

        public void Start()
        {
            _watcher?.Start();
            KillForbiddenProcesses();
        }

        public void Stop()
        {
            _watcher?.Stop();
        }

        public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
        {
            Action<ProcessEvent, string> handler = (eventType, processName) =>
            {
                _ = onEvent(CreateMonitorEvent(eventType, processName)).ContinueWith(
                    static t => _ = t.Exception,
                    CancellationToken.None,
                    TaskContinuationOptions.OnlyOnFaulted,
                    TaskScheduler.Default);
            };

            ProcessViolationDetected += handler;
            Start();

            try
            {
                await Task.Delay(Timeout.Infinite, ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                // expected on shutdown
            }
            finally
            {
                ProcessViolationDetected -= handler;
                Stop();
            }
        }

        private void OnProcessStarted(object sender, EventArrivedEventArgs e)
        {
            var processName = e.NewEvent["ProcessName"]?
                .ToString()?
                .Replace(".exe", "", StringComparison.OrdinalIgnoreCase)
                .ToLowerInvariant();

            if (string.IsNullOrWhiteSpace(processName))
                return;

            if (!_forbiddenProcesses.Contains(processName))
                return;

            KillProcessByName(processName);
        }

        private void KillForbiddenProcesses()
        {
            foreach (var forbiddenProcess in _forbiddenProcesses)
            {
                KillProcessByName(forbiddenProcess);
            }
        }

        private void KillProcessByName(string processName)
        {
            foreach (var process in Process.GetProcessesByName(processName))
            {
                try
                {
                    process.Kill();
                    ProcessViolationDetected?.Invoke(ProcessEvent.ForbiddenProcessKilled, process.ProcessName);
                }
                catch
                {
                    ProcessViolationDetected?.Invoke(ProcessEvent.ForbiddenProcessKillFailed, process.ProcessName);
                }
                finally
                {
                    process.Dispose();
                }
            }
        }

        private MonitorEvent CreateMonitorEvent(ProcessEvent eventType, string processName)
        {
            return eventType switch
            {
                ProcessEvent.ForbiddenProcessKilled =>
                    new MonitorEvent(Name, $"Forbidden process killed: {processName}", Severity.Warning),

                ProcessEvent.ForbiddenProcessKillFailed =>
                    new MonitorEvent(Name, $"Failed to kill forbidden process: {processName}", Severity.Warning),

                _ =>
                    new MonitorEvent(Name, $"Unhandled process event: {eventType} ({processName})", Severity.Warning)
            };
        }

        public void Dispose()
        {
            Stop();

            if (_watcher != null)
            {
                _watcher.EventArrived -= OnProcessStarted;
                _watcher.Dispose();
            }
        }
    }
}