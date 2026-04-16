using Microsoft.Win32;
using Daemon.Abstractions;

namespace Daemon.Monitors
{
    internal class ProcessBlocker : IMitigator
    {
        public string Name => "ProcessBlocker";

        private readonly string[] _forbiddenProcesses =
        [
            "slack"
        ];

        private readonly string[] _scanLocations =
            [
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86)
        ];

        private readonly List<string> _blockedPaths = new List<string>();

        private const string RegistryPath = @"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Image File Execution Options\";

        public void Apply()
        {
            foreach (var location in _scanLocations)
            {
                if (!Directory.Exists(location)) continue;
                Console.WriteLine($"Scanning {location}");

                foreach (var process in _forbiddenProcesses)
                {

                    var options = new EnumerationOptions { RecurseSubdirectories = true, IgnoreInaccessible = true };
                    var files = Directory.GetFiles(location, $"{process}.exe", options);
                    Console.WriteLine($"Found {files.Length} files for {process}");
                    foreach (var file in files)
                    {
                        Console.WriteLine($"Found: {file}");
                        BlockExecutable(file);
                    }
                }
            }
        }

        private void BlockExecutable(string path)
        {
            var filename = Path.GetFileName(path);
            var keyPath = RegistryPath + filename;

            using var key = Registry.LocalMachine.CreateSubKey(keyPath);
            key?.SetValue("Debugger", "fake-debugger");

            _blockedPaths.Add(filename);
        }

        public void Dispose()
        {
            foreach (var fileName in _blockedPaths)
            {
                var keyPath = RegistryPath + fileName;
                Registry.LocalMachine.DeleteSubKeyTree(keyPath, false);
            }
        }
    }
}
