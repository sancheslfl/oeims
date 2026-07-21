using Contracts;
using Microsoft.Win32;

namespace OEIMS.Sentinel.Service.Mitigators;

/// <summary>
/// Prevents known forbidden executables from launching.
/// </summary>
/// <remarks>
/// It changes local Windows state so a forbidden executable is redirected through 
/// Image File Execution Options (IFEO).
/// <para>
/// Correlation with <c>ProcessMonitor</c>: <c>ProcessBlocker</c> tries to prevent future launches,
/// while <c>ProcessMonitor</c> detects and kills processes that are already running or still manage to start.
/// </para>
/// </remarks>
internal class ProcessBlocker : IMitigator
{
    /// <summary>
    /// Name used in logs and diagnostics.
    /// </summary>
    public string Name => nameof(ProcessBlocker);

    private readonly string[] _forbiddenProcesses =
    [
        "slack"
    ];

    /// <summary>
    /// Common installation folders scanned for forbidden executables.
    /// </summary>
    private readonly string[] _scanLocations =
    [
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)
    ];

    private readonly List<string> _blockedPaths = [];

    private const string RegistryPath = @"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\";

    /// <summary>
    /// Scans known locations and blocks each forbidden executable found.
    /// </summary>
    public void Apply()
    {
        foreach (var location in _scanLocations)
        {
            if (!Directory.Exists(location)) continue;

            foreach (var process in _forbiddenProcesses)
            {
                var options = new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true };
                var files = Directory.GetFiles(location, $"{process}.exe", options);
                foreach (var file in files)
                {
                    BlockExecutable(file);
                }
            }
        }
    }

    /// <summary>
    /// Blocks one executable by adding a Windows Image File Execution Options debugger entry.
    /// </summary>
    /// <param name="path">Full path to the executable found in the scan.</param>
    private void BlockExecutable(string path)
    {
        var filename = Path.GetFileName(path);
        var keyPath = RegistryPath + filename;

        using var key = Registry.LocalMachine.CreateSubKey(keyPath);
        key?.SetValue("Debugger", "fake-debugger");

        _blockedPaths.Add(filename);
    }

    /// <summary>
    /// Removes registry entries created by <see cref="Apply" />.
    /// </summary>
    public void Dispose()
    {
        foreach (var fileName in _blockedPaths)
        {
            var keyPath = RegistryPath + fileName;
            Registry.LocalMachine.DeleteSubKeyTree(keyPath, false);
        }
    }
}
