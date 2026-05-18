namespace Daemon.Domain.Platform;

internal interface IClipboardSource : IDisposable
{
    void Block();
    void Unblock();
}
