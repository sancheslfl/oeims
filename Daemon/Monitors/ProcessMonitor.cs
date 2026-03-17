using System.Diagnostics;
using System.Runtime.InteropServices;

namespace Daemon.Monitors
{
    internal class ProcessMonitor
    {
        private static readonly string[] ForbiddenProcesses =
            [
                "slack"
            ];

        internal IEnumerable<string> GetForbiddenProcesses()
        {
            return Process.GetProcesses()
                .Select(p => p.ProcessName.ToLower())
                .Where(name => ForbiddenProcesses.Contains(name));
        }

        internal void KillForbiddenProcesses()
        {
            var forbiddenProcesses = GetForbiddenProcesses();
            foreach (var process in forbiddenProcesses)
            {
                try
                {
                    Process.GetProcessesByName(process).FirstOrDefault()?.Kill();
                }
                catch (Exception ex)
                {
                    // Log the exception or handle it as needed
                }
            }
        }
    }
}