using Daemon.Domain.Platform;
using Daemon.Platform.Windows.Native;

namespace Daemon.Platform.Windows;

internal sealed class WinClipboardSource : IClipboardSource
{
    private IntPtr _hwnd = IntPtr.Zero;
    private Thread? _thread;

    public void Block()
    {
        var ready = new ManualResetEventSlim(false);

        _thread = new Thread(() =>
        {
            _hwnd = User32.CreateWindowEx(0, "STATIC", string.Empty, 0, 0, 0, 0, 0, WinEventConstants.HWND_MESSAGE, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
            User32.OpenClipboard(_hwnd);
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
        User32.CloseClipboard();

        if (_hwnd != IntPtr.Zero)
        {
            User32.DestroyWindow(_hwnd);
            _hwnd = IntPtr.Zero;
        }

        _thread?.Interrupt();
        _thread = null;
    }

    public void Dispose() => Unblock();
}
