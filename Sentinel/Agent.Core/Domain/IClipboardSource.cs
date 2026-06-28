namespace OEIMS.Sentinel.Agent.Domain;

public interface IClipboardSource : IDisposable
{
    void Block();
    void Unblock();
}
