namespace Contracts
{
    /// <summary>
    /// Component that actively reduces an exam integrity risk on the local machine.
    /// </summary>
    /// <remarks>
    /// A mitigator prevents or blocks. It is different from a <see cref="IMonitor" />, which only observes and reports.
    /// Mitigators must clean up their changes in <see cref="IDisposable.Dispose" /> when possible.
    /// </remarks>
    public interface IMitigator : IDisposable
    {
        /// <summary>
        /// Name defined for the component.
        /// </summary>
        string Name { get; }

        /// <summary>
        /// Entry point function that applies the mitigation.
        /// </summary>
        void Apply();
    }
}