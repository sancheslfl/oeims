using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

namespace OEIMS.Sentinel.Agent.Native;
internal static partial class Kernel32
{
    /// <summary>
    /// Retrieves the identifier of the calling thread.
    /// Use this when another API needs the current Win32 thread ID.
    /// </summary>
    [LibraryImport("kernel32.dll")]
    internal static partial uint GetCurrentThreadId();
}
