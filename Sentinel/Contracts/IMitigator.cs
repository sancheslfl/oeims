namespace Contracts
{
    /// <summary>
    /// Component that actively reduces an exam-integrity risk on the local machine.
    /// </summary>
    /// <remarks>
    /// A mitigator prevents or blocks. It is different from an <see cref="IMonitor" />, which only observes and reports.
    /// Mitigators must clean up their changes in <see cref="IDisposable.Dispose" /> when possible.
    /// </remarks>
    public interface IMitigator : IDisposable
    {
        /// <summary>
        /// Stable name used in logs and diagnostics.
        /// </summary>
        string Name { get; }

        /// <summary>
        /// Applies the mitigation before the exam monitoring flow starts.
        /// </summary>
        void Apply();
    }
}