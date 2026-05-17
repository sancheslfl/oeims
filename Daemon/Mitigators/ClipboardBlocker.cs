using Daemon.Domain;
using System.Runtime.InteropServices;

internal class ClipboardBlocker : IMitigator
{
    public string Name => nameof(ClipboardBlocker);

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

    private static readonly IntPtr HWND_MESSAGE = new(-3);

    private IntPtr _hwnd = IntPtr.Zero;
    private Thread? _thread;

    public void Apply()
    {
        var ready = new ManualResetEventSlim(false);

        _thread = new Thread(() =>
        {
            _hwnd = CreateWindowEx(0, "STATIC", null!, 0, 0, 0, 0, 0, HWND_MESSAGE, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);
            OpenClipboard(_hwnd);
            ready.Set();

            // Keep the thread alive so the window (and clipboard ownership) persists
            Thread.Sleep(Timeout.Infinite);
        });

        _thread.IsBackground = true;
        _thread.SetApartmentState(ApartmentState.STA);
        _thread.Start();

        ready.Wait();
    }

    public void Dispose()
    {
        CloseClipboard();
        if (_hwnd != IntPtr.Zero)
        {
            DestroyWindow(_hwnd);
            _hwnd = IntPtr.Zero;
        }
        _thread?.Interrupt();
    }
}