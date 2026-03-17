using System.Runtime.InteropServices;

namespace Daemon.Monitors
{
    internal class ClipboardMonitor: IDisposable
    {
        [DllImport("user32.dll")]
        private static extern bool OpenClipboard(IntPtr hWndNewOwner);

        [DllImport("user32.dll")]
        private static extern bool CloseClipboard();


        internal void BlockClipboard()
        {
           _ = OpenClipboard(IntPtr.Zero);
        }

        public void Dispose()
        {
            CloseClipboard();
        }

    }
}