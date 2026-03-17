using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

namespace Daemon.Monitors
{
    internal class FocusMonitor
    {
        private const string ExamWindowTitle = "OEIMS Exam";

        [DllImport("user32.dll")]
        private static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll")]
        private static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);

        internal string GetForegroundWindowTitle()
        {
            IntPtr handle = GetForegroundWindow();
            StringBuilder title = new StringBuilder(256);
            GetWindowText(handle, title, 256);
            return title.ToString();
        }

        internal bool IsExamWindowFocused()
        {
            return GetForegroundWindowTitle().Contains(ExamWindowTitle);
        }
    }
}
