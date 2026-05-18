using System.Diagnostics;
using System.Text;
using Daemon.Domain.Platform;
using Daemon.Platform.Windows.Native;

namespace Daemon.Platform.Windows;

internal sealed class WinActiveWindowSource : IActiveWindowSource
{
    private IntPtr _foregroundHook = IntPtr.Zero;
    private IntPtr _nameChangeHook = IntPtr.Zero;

    private IntPtr _foregroundHwnd = IntPtr.Zero;
    private string _lastName = string.Empty;

    private User32.WinEventDelegate? _foregroundCallback;
    private User32.WinEventDelegate? _nameChangeCallback;

    public Task StartAsync(Func<ActiveWindowInfo, Task> onChanged, CancellationToken ct)
    {
        var completion = new TaskCompletionSource(
            TaskCreationOptions.RunContinuationsAsynchronously);

        var thread = new Thread(() =>
        {
            try
            {
                RegisterHooks(onChanged);
                RunMessageLoop(ct);

                completion.TrySetResult();
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

        thread.Name = nameof(WinActiveWindowSource);
        thread.IsBackground = true;
        thread.SetApartmentState(ApartmentState.STA);
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
        var name = GetWindowName(hwnd);

        if (string.IsNullOrWhiteSpace(name))
            name = "<unknown>";

        if (name == _lastName)
            return;

        _lastName = name;

        _ = NotifyAsync(name, onChanged);
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
        User32.GetWindowThreadProcessId(hwnd, out var processId);

        if (processId == 0)
            return string.Empty;

        try
        {
            using var process = Process.GetProcessById((int)processId);
            return process.ProcessName;
        }
        catch
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
            // ignore because the Windows hook thread should not crash because an event consumer failed.
        }
    }

    private static void RunMessageLoop(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            if (User32.PeekMessage(out var msg, IntPtr.Zero, 0, 0, WinEventConstants.PM_REMOVE))
            {
                User32.TranslateMessage(ref msg);
                User32.DispatchMessage(ref msg);
            }
            else
            {
                ct.WaitHandle.WaitOne(100);
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