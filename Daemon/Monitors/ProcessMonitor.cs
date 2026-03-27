using System.Diagnostics;
using System.Management;

namespace Daemon.Monitors
{
    internal class ProcessMonitor : IDisposable
    {
        private readonly ManagementEventWatcher? _watcher;
        private readonly string[] _forbiddenProcesses =
        [
            "slack"
        ];

        internal ProcessMonitor()
        {
            _watcher = new ManagementEventWatcher(
            new WqlEventQuery("SELECT * FROM Win32_ProcessStartTrace"));
            _watcher.EventArrived += OnProcessStarted;
        }

        internal void StartWatching()
        {
            _watcher.Start();
        }

        private void OnProcessStarted(object sender, EventArrivedEventArgs e)
        {
            var processName = e.NewEvent["ProcessName"]?.ToString()?.Replace(".exe", "").ToLower();

            if (processName != null && _forbiddenProcesses.Contains(processName))
            {
                var processes = Process.GetProcessesByName(processName);
                foreach (var process in processes)
                {
                    process.Kill();
                }
            }
        }

        internal IEnumerable<string> KillForbiddenProcesses()
        {
            var killed = new List<string>();
            foreach (var process in Process.GetProcesses())
            {
                if (_forbiddenProcesses.Contains(process.ProcessName.ToLower()))
                {
                    killed.Add(process.ProcessName);
                    process.Kill();
                }
            }
            return killed;
        }

        public void Dispose()
        {
            _watcher?.Stop();
            _watcher?.Dispose();
        }
    }
}