using Daemon.Domain;
using Daemon.Domain.Platform;

namespace Daemon.Mitigators;

internal sealed class ClipboardBlocker(IClipboardSource clipboardSource) : IMitigator
{
    public string Name => nameof(ClipboardBlocker);

    public void Apply() => clipboardSource.Block();

    public void Dispose() => clipboardSource.Unblock();
}
