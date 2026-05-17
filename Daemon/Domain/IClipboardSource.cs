namespace Daemon.Domain;

internal interface IClipboardSource : IDisposable
{
    void Block();
    void Unblock();
}
