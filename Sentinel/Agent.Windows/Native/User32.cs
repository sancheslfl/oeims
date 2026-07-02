using System;
using System.Runtime.InteropServices;
using System.Text;

namespace OEIMS.Sentinel.Agent.Native;

internal static partial class User32
{
    private const string LibraryName = "user32.dll";

    /// <summary>
    /// Callback invoked by Windows when a registered WinEvent is raised.
    /// Keep the delegate instance alive while the hook is installed.
    /// </summary>
    /// <param name="hWinEventHook">Handle of the WinEvent hook that received the event.</param>
    /// <param name="eventType">WinEvent identifier, such as EVENT_SYSTEM_FOREGROUND.</param>
    /// <param name="hwnd">Window handle related to the event, or zero when there is no window.</param>
    /// <param name="idObject">Object identifier related to the event, such as OBJID_WINDOW.</param>
    /// <param name="idChild">Child object identifier related to the event.</param>
    /// <param name="dwEventThread">Identifier of the thread that generated the event.</param>
    /// <param name="dwmsEventTime">Time, in milliseconds, when the event was generated.</param>
    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    internal delegate void WinEventDelegate(
        IntPtr hWinEventHook,
        uint eventType,
        IntPtr hwnd,
        int idObject,
        int idChild,
        uint dwEventThread,
        uint dwmsEventTime);

    /// <summary>
    /// Registers a callback for a range of WinEvents.
    /// </summary>
    /// <param name="eventMin">Lowest event identifier to monitor.</param>
    /// <param name="eventMax">Highest event identifier to monitor.</param>
    /// <param name="hmodWinEventProc">Module handle containing the callback, or zero for out-of-context managed callbacks.</param>
    /// <param name="lpfnWinEventProc">Callback invoked when a matching event occurs.</param>
    /// <param name="idProcess">Process filter, or zero to receive events from all processes.</param>
    /// <param name="idThread">Thread filter, or zero to receive events from all threads.</param>
    /// <param name="dwFlags">Hook behavior flags, such as WINEVENT_OUTOFCONTEXT.</param>
    /// <returns>Hook handle on success; zero on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "SetWinEventHook", SetLastError = true)]
    internal static partial IntPtr SetWinEventHook(
        uint eventMin,
        uint eventMax,
        IntPtr hmodWinEventProc,
        WinEventDelegate lpfnWinEventProc,
        uint idProcess,
        uint idThread,
        uint dwFlags);

    /// <summary>
    /// Removes a WinEvent hook previously created by SetWinEventHook.
    /// </summary>
    /// <param name="hWinEventHook">Hook handle returned by SetWinEventHook.</param>
    /// <returns>True on success; false on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "UnhookWinEvent", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool UnhookWinEvent(IntPtr hWinEventHook);

    /// <summary>
    /// Copies the title text of a window into a caller-provided UTF-16 buffer.
    /// </summary>
    /// <param name="hWnd">Handle of the window whose title is requested.</param>
    /// <param name="lpString">Buffer that receives the title text.</param>
    /// <param name="nMaxCount">Maximum number of characters to copy, including the null terminator.</param>
    /// <returns>Number of characters copied, excluding the null terminator; zero if no text was copied.</returns>
    [DllImport(
    "user32.dll",
    EntryPoint = "GetWindowTextW",
    CharSet = CharSet.Unicode,
    ExactSpelling = true,
    SetLastError = true)]
    internal static extern int GetWindowText(IntPtr hWnd, [Out] StringBuilder lpString, int nMaxCount);

    /// <summary>
    /// Retrieves the length of a window title.
    /// </summary>
    /// <param name="hWnd">Handle of the window whose title length is requested.</param>
    /// <returns>Title length in characters, excluding the null terminator; zero if the window has no title.</returns>
    [LibraryImport(LibraryName, EntryPoint = "GetWindowTextLengthW", SetLastError = true)]
    internal static partial int GetWindowTextLength(IntPtr hWnd);

    /// <summary>
    /// Retrieves the thread identifier and process identifier for a window.
    /// </summary>
    /// <param name="hWnd">Handle of the window to inspect.</param>
    /// <param name="lpdwProcessId">Receives the process identifier that owns the window.</param>
    /// <returns>Thread identifier that created the window; zero on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "GetWindowThreadProcessId", SetLastError = true)]
    internal static partial uint GetWindowThreadProcessId(
        IntPtr hWnd,
        out uint lpdwProcessId);

    /// <summary>
    /// Checks the calling thread's message queue for a message.
    /// </summary>
    /// <param name="lpMsg">Receives the message data when a message is found.</param>
    /// <param name="hWnd">Window filter, or zero for messages for the current thread.</param>
    /// <param name="wMsgFilterMin">Lowest message identifier to retrieve, or zero for no lower filter.</param>
    /// <param name="wMsgFilterMax">Highest message identifier to retrieve, or zero for no upper filter.</param>
    /// <param name="wRemoveMsg">Queue behavior, such as PM_NOREMOVE or PM_REMOVE.</param>
    /// <returns>True if a message is available; false otherwise.</returns>
    [LibraryImport(LibraryName, EntryPoint = "PeekMessageW", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool PeekMessage(
        out MSG lpMsg,
        IntPtr hWnd,
        uint wMsgFilterMin,
        uint wMsgFilterMax,
        uint wRemoveMsg);

    /// <summary>
    /// Retrieves a message from the calling thread's message queue.
    /// Blocks until a message is available.
    /// </summary>
    /// <param name="lpMsg">Receives the retrieved message.</param>
    /// <param name="hWnd">Window filter, or zero for messages for the current thread.</param>
    /// <param name="wMsgFilterMin">Lowest message identifier to retrieve, or zero for no lower filter.</param>
    /// <param name="wMsgFilterMax">Highest message identifier to retrieve, or zero for no upper filter.</param>
    /// <returns>Greater than zero for a normal message, zero for WM_QUIT, and -1 on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "GetMessageW", SetLastError = true)]
    internal static partial int GetMessage(
        out MSG lpMsg,
        IntPtr hWnd,
        uint wMsgFilterMin,
        uint wMsgFilterMax);

    /// <summary>
    /// Posts a message to the message queue of a specific thread.
    /// The target thread must already have a message queue.
    /// </summary>
    /// <param name="idThread">Identifier of the target thread.</param>
    /// <param name="Msg">Message identifier to post.</param>
    /// <param name="wParam">First message-specific value.</param>
    /// <param name="lParam">Second message-specific value.</param>
    /// <returns>True if the message was posted; false on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "PostThreadMessageW", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool PostThreadMessage(
        uint idThread,
        uint Msg,
        UIntPtr wParam,
        IntPtr lParam);

    /// <summary>
    /// Translates virtual-key messages into character messages.
    /// Usually called before DispatchMessage in a normal message loop.
    /// </summary>
    /// <param name="lpMsg">Message to translate.</param>
    /// <returns>True if a character message was posted; false otherwise.</returns>
    [LibraryImport(LibraryName, EntryPoint = "TranslateMessage")]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool TranslateMessage(ref MSG lpMsg);

    /// <summary>
    /// Dispatches a message to its target window procedure.
    /// Thread messages without a target window are not meaningfully dispatched.
    /// </summary>
    /// <param name="lpMsg">Message to dispatch.</param>
    /// <returns>Result returned by the target window procedure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "DispatchMessageW")]
    internal static partial IntPtr DispatchMessage(ref MSG lpMsg);

    /// <summary>
    /// Opens the clipboard for the current task.
    /// </summary>
    /// <param name="hWndNewOwner">Window handle associated with the open clipboard, or zero.</param>
    /// <returns>True if the clipboard was opened; false on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "OpenClipboard", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool OpenClipboard(IntPtr hWndNewOwner);

    /// <summary>
    /// Closes the clipboard after a successful OpenClipboard call.
    /// </summary>
    /// <returns>True if the clipboard was closed; false on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "CloseClipboard", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool CloseClipboard();

    /// <summary>
    /// Creates a window using UTF-16 class and window names.
    /// </summary>
    /// <param name="dwExStyle">Extended window style.</param>
    /// <param name="lpClassName">Registered window class name.</param>
    /// <param name="lpWindowName">Window name, or null.</param>
    /// <param name="dwStyle">Window style.</param>
    /// <param name="x">Initial horizontal position.</param>
    /// <param name="y">Initial vertical position.</param>
    /// <param name="nWidth">Initial width.</param>
    /// <param name="nHeight">Initial height.</param>
    /// <param name="hWndParent">Parent or owner window handle, or zero.</param>
    /// <param name="hMenu">Menu handle or child-window identifier, or zero.</param>
    /// <param name="hInstance">Instance handle associated with the window.</param>
    /// <param name="lpParam">Optional creation data passed to the window procedure.</param>
    /// <returns>Window handle on success; zero on failure.</returns>
    [LibraryImport(
        LibraryName,
        EntryPoint = "CreateWindowExW",
        SetLastError = true,
        StringMarshalling = StringMarshalling.Utf16)]
    internal static partial IntPtr CreateWindowEx(
        uint dwExStyle,
        string lpClassName,
        string? lpWindowName,
        uint dwStyle,
        int x,
        int y,
        int nWidth,
        int nHeight,
        IntPtr hWndParent,
        IntPtr hMenu,
        IntPtr hInstance,
        IntPtr lpParam);

    /// <summary>
    /// Destroys a window created by the calling thread.
    /// </summary>
    /// <param name="hWnd">Handle of the window to destroy.</param>
    /// <returns>True if the window was destroyed; false on failure.</returns>
    [LibraryImport(LibraryName, EntryPoint = "DestroyWindow", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    internal static partial bool DestroyWindow(IntPtr hWnd);
}