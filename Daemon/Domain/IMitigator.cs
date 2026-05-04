namespace Daemon.Domain
{
    public interface IMitigator : IDisposable
    {
        string Name { get; }
        void Apply();
    }
}
