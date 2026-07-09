namespace OEIMS.Sentinel.Service.Domain.Platform;

/// <summary>
/// Boundary between execution-blocking policy and the operating system mechanism that prevents an
/// executable from launching.
/// </summary>
/// <remarks>
/// <see cref="OEIMS.Sentinel.Service.Mitigators.ProcessBlocker" /> depends on this abstraction so its
/// policy can be tested without touching the Windows registry or the file system.
/// </remarks>
public interface IExecutionBlockSource : IDisposable
{
    /// <summary>
    /// Prevents every executable matching <paramref name="processName" /> from launching.
    /// </summary>
    /// <param name="processName">
    /// Executable name without path or extension, for example <c>slack</c>.
    /// </param>
    void Block(string processName);

    /// <summary>
    /// Reverts every block previously applied through <see cref="Block" />, restoring normal launch behaviour.
    /// </summary>
    void UnblockAll();
}
