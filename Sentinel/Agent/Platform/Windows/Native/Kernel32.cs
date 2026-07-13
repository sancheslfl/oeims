
using System.Runtime.InteropServices;

namespace OEIMS.Sentinel.Agent.Native;
internal static partial class Kernel32
{
    private const string LibraryName = "kernel32.dll";

    /// <summary>
    /// Retrieves the identifier of the calling thread.
    /// </summary>
    [LibraryImport(LibraryName)]
    internal static partial uint GetCurrentThreadId();
}
