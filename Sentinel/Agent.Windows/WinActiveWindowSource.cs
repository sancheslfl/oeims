using System.Diagnostics;
using System.Globalization;
using System.Text;
using OEIMS.Sentinel.Agent.Domain;
using OEIMS.Sentinel.Agent.Native;

namespace OEIMS.Sentinel.Agent;

internal sealed class WinActiveWindowSource : IActiveWindowSource
{
    private static readonly int CurrentProcessId = Environment.ProcessId;

    private IntPtr _foregroundHook = IntPtr.Zero;
    private IntPtr _nameChangeHook = IntPtr.Zero;

    private IntPtr _foregroundHwnd = IntPtr.Zero;
    private string _lastWindowKey = string.Empty;

    private User32.WinEventDelegate? _foregroundCallback;
    private User32.WinEventDelegate? _nameChangeCallback;

    public Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct)
    {
        var completion = new TaskCompletionSource<bool>(
            TaskCreationOptions.RunContinuationsAsynchronously);

        var thread = new Thread(() =>
        {
            try
            {
                RegisterHooks(onChanged);
                RunMessageLoop(ct);

                completion.TrySetResult(true);
            }
            catch (Exception ex)
            {
                completion.TrySetException(ex);
            }
            finally
            {
                UnregisterHooks();
            }
        });

        thread.Name = nameof(WinActiveWindowSource);    // TODO: Put blank space dynamically in the middle
        thread.IsBackground = true;
        thread.Start();

        return completion.Task;
    }

    private void RegisterHooks(Func<ActiveWindowInfo, Task> onChanged)
    {
        _foregroundCallback = (_, _, hwnd, _, _, _, _) =>
        {
            if (hwnd == IntPtr.Zero)
                return;

            _foregroundHwnd = hwnd;
            NotifyIfChanged(hwnd, onChanged);
        };

        _nameChangeCallback = (_, _, hwnd, idObject, _, _, _) =>
        {
            if (hwnd == IntPtr.Zero)
                return;

            if (idObject != WinEventConstants.OBJID_WINDOW)
                return;

            if (hwnd != _foregroundHwnd)
                return;

            NotifyIfChanged(hwnd, onChanged);
        };

        _foregroundHook = User32.SetWinEventHook(
            WinEventConstants.EVENT_SYSTEM_FOREGROUND,
            WinEventConstants.EVENT_SYSTEM_FOREGROUND,
            IntPtr.Zero,
            _foregroundCallback,
            0,
            0,
            WinEventConstants.WINEVENT_OUTOFCONTEXT);

        _nameChangeHook = User32.SetWinEventHook(
            WinEventConstants.EVENT_OBJECT_NAMECHANGE,
            WinEventConstants.EVENT_OBJECT_NAMECHANGE,
            IntPtr.Zero,
            _nameChangeCallback,
            0,
            0,
            WinEventConstants.WINEVENT_OUTOFCONTEXT);

        if (_foregroundHook == IntPtr.Zero || _nameChangeHook == IntPtr.Zero)
            throw new InvalidOperationException("Failed to register Windows focus hooks.");
    }

    private void NotifyIfChanged(IntPtr hwnd, Func<ActiveWindowInfo, Task> onChanged)
    {
        var processId = GetProcessId(hwnd);

        if (processId == 0 || processId == CurrentProcessId)
            return;

        var name = GetWindowName(hwnd);

        if (string.IsNullOrWhiteSpace(name))
            name = "Unknown window";

        var windowKey = string.Create(
            CultureInfo.InvariantCulture,
            $"{processId}:{name}");

        if (windowKey == _lastWindowKey)
            return;

        _lastWindowKey = windowKey;

        _ = NotifyAsync(name, onChanged);
    }

    private static int GetProcessId(IntPtr hwnd)
    {
        var threadId = User32.GetWindowThreadProcessId(hwnd, out var processId);

        if (threadId == 0 || processId == 0 || processId > int.MaxValue)
            return 0;

        return (int)processId;
    }

    private static string GetWindowName(IntPtr hwnd)
    {
        var title = GetWindowTitle(hwnd);

        if (!string.IsNullOrWhiteSpace(title))
            return title;

        return GetProcessName(hwnd);
    }

    private static string GetWindowTitle(IntPtr hwnd)
    {
        var length = User32.GetWindowTextLength(hwnd);

        if (length <= 0)
            return string.Empty;

        var builder = new StringBuilder(length + 1);

        var copied = User32.GetWindowText(
            hwnd,
            builder,
            builder.Capacity);

        return copied <= 0
            ? string.Empty
            : builder.ToString();
    }

    private static string GetProcessName(IntPtr hwnd)
    {
        var processId = GetProcessId(hwnd);

        if (processId == 0)
            return string.Empty;

        try
        {
            using var process = Process.GetProcessById(processId);
            return process.ProcessName;
        }
        catch (ArgumentException)
        {
            return string.Empty;
        }
        catch (InvalidOperationException)
        {
            return string.Empty;
        }
    }

    private static async Task NotifyAsync(string name, Func<ActiveWindowInfo, Task> onChanged)
    {
        try
        {
            await onChanged(new ActiveWindowInfo(name));
        }
        catch
        {
            // hook thread must not crash because an event consumer failed.
        }
    }

    private static void RunMessageLoop(CancellationToken ct)
    {
        var loopThread = Kernel32.GetCurrentThreadId();

        User32.PeekMessage(
            out _,
            IntPtr.Zero,
            WinEventConstants.WM_USER_STOP,
            WinEventConstants.WM_USER_STOP,
            WinEventConstants.PM_NOREMOVE
        );

        using (ct.Register(() =>
            User32.PostThreadMessage(loopThread, WinEventConstants.WM_USER_STOP, 0, 0)   
        ))
        {
            while (true)
            {
                var res = User32.GetMessage(out var msg, IntPtr.Zero, 0, 0);

                if (res <= 0)   // error or WM_QUIT
                    break;

                if (ct.IsCancellationRequested)
                    break;

                User32.TranslateMessage(ref msg);
                User32.DispatchMessage(ref msg);

            }
        }
    }

    private void UnregisterHooks()
    {
        if (_foregroundHook != IntPtr.Zero)
        {
            User32.UnhookWinEvent(_foregroundHook);
            _foregroundHook = IntPtr.Zero;
        }

        if (_nameChangeHook != IntPtr.Zero)
        {
            User32.UnhookWinEvent(_nameChangeHook);
            _nameChangeHook = IntPtr.Zero;
        }
    }

    public void Dispose()
    {
        UnregisterHooks();
    }
}