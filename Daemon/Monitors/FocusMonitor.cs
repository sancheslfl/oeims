using System.Runtime.InteropServices;
using System.Text;
using Daemon.Abstractions;

namespace Daemon.Monitors
{
    internal class FocusMonitor : IMonitor
    {
        public string Name => "FocusMonitor";

        private const string ExamWindowTitle = "OEIMS Exam";

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

        private string GetForegroundWindowTitle()
        {
            IntPtr handle = GetForegroundWindow();
            StringBuilder title = new StringBuilder(256);
            GetWindowText(handle, title, 256);
            return title.ToString();
        }

        private bool IsExamWindowFocused()
        {
            return GetForegroundWindowTitle().Contains(ExamWindowTitle);
        }

        public async Task StartAsync(Func<MonitorEvent, Task> onEvent, CancellationToken ct)
        {
            try
            {
                while (!ct.IsCancellationRequested)
                {
                    if (!IsExamWindowFocused())
                    {
                        var title = GetForegroundWindowTitle();
                        await onEvent(new MonitorEvent(Name, $"Focus lost: {title}", Severity.Warning));
                    }

                    await Task.Delay(1000, ct);
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                return;
            }
        }

        public void Dispose() { }
    }
}
