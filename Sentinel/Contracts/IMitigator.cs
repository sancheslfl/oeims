namespace Contracts
{
    public interface IMitigator : IDisposable
    {
        string Name { get; }
        void Apply();
    }
}
