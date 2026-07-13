namespace OEIMS.Sentinel.Agent.Native;

internal static class WinEventConstants
{
    public const uint EVENT_SYSTEM_FOREGROUND = 0x0003;
    public const uint EVENT_OBJECT_NAMECHANGE = 0x800C;

    public const uint WINEVENT_OUTOFCONTEXT = 0x0000;

    /// <summary>
    /// Base value for application window messages.
    /// </summary>
    internal const uint WM_USER = 0x0400;

    /// <summary>
    /// Private message used only to wake the message loop when cancellation is requested.
    /// It is not a foreground window event.
    /// </summary>
    internal const uint WM_USER_STOP = WM_USER + 1;

    /// <summary>
    /// Tells PeekMessage to leave the message in the queue.
    /// Useful when only forcing the thread message queue to exist.
    /// </summary>
    internal const uint PM_NOREMOVE = 0x0000;

    public const uint PM_REMOVE = 0x0001;

    public const int OBJID_WINDOW = 0;

    public static readonly IntPtr HWND_MESSAGE = new(-3);
}