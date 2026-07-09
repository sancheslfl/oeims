using Microsoft.Win32;
using OEIMS.Sentinel.Service.Domain.Platform;

namespace OEIMS.Sentinel.Service.Platform.Windows;

/// <summary>
/// Windows implementation of <see cref="IExecutionBlockSource" />. Prevents an executable from launching by
/// adding an Image File Execution Options (IFEO) debugger entry that redirects its launch to a command that
/// does not exist, so the launch fails.
/// </summary>
internal sealed class WinExecutionBlockSource : IExecutionBlockSource
{
    private const string RegistryPath =
        @"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\";

    /// <summary>
    /// Common installation folders scanned for forbidden executables.
    /// </summary>
    private static readonly string[] ScanLocations =
    [
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
        Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)
    ];

    private readonly List<string> _blockedKeys = new();

    /// <inheritdoc />
    public void Block(string processName)
    {
        if (string.IsNullOrWhiteSpace(processName))
            return;

        foreach (var location in ScanLocations)
        {
            if (!Directory.Exists(location)) continue;

            var options = new EnumerationOptions
            {
                RecurseSubdirectories = true,
                IgnoreInaccessible = true
            };

            var files = Directory.GetFiles(location, $"{processName}.exe", options);

            foreach (var file in files)
            {
                BlockExecutable(Path.GetFileName(file));
            }
        }
    }

    /// <summary>
    /// Adds a single Image File Execution Options debugger entry keyed by executable file name.
    /// </summary>
    private void BlockExecutable(string fileName)
    {
        var keyPath = RegistryPath + fileName;

        using var key = Registry.LocalMachine.CreateSubKey(keyPath);
        key?.SetValue("Debugger", "fake-debugger");

        _blockedKeys.Add(fileName);
    }

    /// <inheritdoc />
    public void UnblockAll()
    {
        foreach (var fileName in _blockedKeys)
        {
            var keyPath = RegistryPath + fileName;
            Registry.LocalMachine.DeleteSubKeyTree(keyPath, false);
        }

        _blockedKeys.Clear();
    }

    public void Dispose()
    {
        UnblockAll();
    }
}
