namespace OEIMS.Sentinel.Agent.Domain;

internal interface IClipboardSource : IDisposable
{
    void Block();
    void Unblock();
}
