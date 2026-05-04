namespace Daemon.Platform.Windows.Native;

internal static class WinEventConstants
{
    public const uint EVENT_SYSTEM_FOREGROUND = 0x0003;
    public const uint EVENT_OBJECT_NAMECHANGE = 0x800C;

    public const uint WINEVENT_OUTOFCONTEXT = 0x0000;

    public const uint PM_REMOVE = 0x0001;

    public const int OBJID_WINDOW = 0;
}