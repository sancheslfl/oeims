using System.Runtime.InteropServices;
using Daemon.Domain.Platform;

namespace Daemon.Platform.Windows;

internal sealed class WinClipboardSource : IClipboardSource
{
    [DllImport("user32.dll")]
    private static extern bool OpenClipboard(IntPtr hWndNewOwner);

    [DllImport("user32.dll")]
    private static extern bool CloseClipboard();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr CreateWindowEx(
        uint dwExStyle, string lpClassName, string lpWindowName,
        uint dwStyle, int x, int y, int nWidth, int nHeight,
        IntPtr hWndParent, IntPtr hMenu, IntPtr hInstance, IntPtr lpParam);

    [DllImport("user32.dll")]
    private static extern bool DestroyWindow(IntPtr hWnd);

    private static readonly IntPtr HwndMessage = new(-3);

    private IntPtr _hwnd = IntPtr.Zero;
    private Thread? _thread;

    public void Block()
    {
        var ready = new ManualResetEventSlim(false);

        _thread = new Thread(() =>
        {
            _hwnd = CreateWindowEx(0, "STATIC", string.Empty, 0, 0, 0, 0, 0, HwndMessage, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
            OpenClipboard(_hwnd);
            ready.Set();
            Thread.Sleep(Timeout.Infinite);
        });

        _thread.Name = nameof(WinClipboardSource);
        _thread.IsBackground = true;
        _thread.SetApartmentState(ApartmentState.STA);
        _thread.Start();

        ready.Wait();
    }

    public void Unblock()
    {
        CloseClipboard();

        if (_hwnd != IntPtr.Zero)
        {
            DestroyWindow(_hwnd);
            _hwnd = IntPtr.Zero;
        }

        _thread?.Interrupt();
        _thread = null;
    }

    public void Dispose() => Unblock();
}
