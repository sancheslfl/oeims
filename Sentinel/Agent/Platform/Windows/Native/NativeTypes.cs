using System.Runtime.InteropServices;

namespace OEIMS.Sentinel.Agent.Platform.Windows.Native;

[StructLayout(LayoutKind.Sequential)]
internal struct MSG
{
    /// <summary>
    /// Handle of the window that should receive the message.
    /// Can be zero for thread messages.
    /// </summary>
    public IntPtr hwnd;

    /// <summary>
    /// Message identifier, such as WM_QUIT, WM_APP_STOP, or other Win32 messages.
    /// </summary>
    public uint message;

    /// <summary>
    /// First message-specific value.
    /// Meaning depends on the message type.
    /// </summary>
    public IntPtr wParam;

    /// <summary>
    /// Second message-specific value.
    /// Meaning depends on the message type.
    /// </summary>
    public IntPtr lParam;

    /// <summary>
    /// Time at which the message was posted.
    /// </summary>
    public uint time;

    /// <summary>
    /// Cursor position when the message was posted.
    /// </summary>
    public POINT pt;
}

[StructLayout(LayoutKind.Sequential)]
internal struct POINT
{
    public int x;
    public int y;
}