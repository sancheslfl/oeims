using Microsoft.Extensions.Logging.Abstractions;
using OEIMS.Sentinel.Service.Connections.Server;
using Xunit;

namespace Tests.Service.Connections.Server;

public sealed class ServerSessionTests
{
    [Fact(DisplayName = "Authorizing stores the session and releases authorization waiters")]
    public async Task AuthorizingStoresTheSessionAndReleasesAuthorizationWaiters()
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var waitTask = session.WaitUntilAuthorizedAsync(cts.Token);

        session.Authorize("token-1", "participant-1");

        var authorization = await waitTask;

        Assert.True(session.IsAuthorized);
        Assert.Equal(new ServerAuthorization("token-1", "participant-1"), authorization);
        Assert.True(File.Exists(file.Path));
        Assert.True(session.TryGetAuthorization(out var current));
        Assert.Equal(authorization, current);
    }

    [Fact(DisplayName = "A persisted authorization is restored when the session starts")]
    public void APersistedAuthorizationIsRestoredWhenTheSessionStarts()
    {
        using var file = TempSessionFile.Create();

        CreateSession(file.Path).Authorize("token-1", "participant-1");

        var restored = CreateSession(file.Path);

        Assert.True(restored.IsAuthorized);
        Assert.Equal(
            new ServerAuthorization("token-1", "participant-1"),
            restored.GetAuthorization());
    }

    [Fact(DisplayName = "Clearing the session removes authorization and releases change waiters")]
    public async Task ClearingTheSessionRemovesAuthorizationAndReleasesChangeWaiters()
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);
        session.Authorize("token-1", "participant-1");

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var waitTask = session.WaitUntilAuthorizationChangedAsync(cts.Token);

        session.Clear();

        await waitTask;

        Assert.False(session.IsAuthorized);
        Assert.False(File.Exists(file.Path));
        Assert.False(session.TryGetAuthorization(out _));
        Assert.Throws<InvalidOperationException>(() => session.GetAuthorization());
    }

    [Fact(DisplayName = "Waiting for authorization changes completes when authorization changes")]
    public async Task WaitingForAuthorizationChangesCompletesWhenAuthorizationChanges()
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var waitTask = session.WaitUntilAuthorizationChangedAsync(cts.Token);

        session.Authorize("token-1", "participant-1");

        await waitTask;
    }

    [Theory(DisplayName = "Authorizing rejects empty values")]
    [InlineData("", "participant-1", "token")]
    [InlineData("   ", "participant-1", "token")]
    [InlineData("token-1", "", "participantId")]
    [InlineData("token-1", "   ", "participantId")]
    public void AuthorizingRejectsEmptyValues(
        string token,
        string participantId,
        string expectedParameter)
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);

        var exception = Assert.Throws<ArgumentException>(() =>
            session.Authorize(token, participantId));

        Assert.Equal(expectedParameter, exception.ParamName);
        Assert.False(session.IsAuthorized);
        Assert.False(File.Exists(file.Path));
    }

    [Fact(DisplayName = "Old authorizations stop being current after authorization changes")]
    public void OldAuthorizationsStopBeingCurrentAfterAuthorizationChanges()
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);

        session.Authorize("token-1", "participant-1");
        var first = session.GetAuthorization();

        session.Authorize("token-2", "participant-1");

        Assert.False(session.IsCurrent(first));
        Assert.True(session.IsCurrent(new ServerAuthorization("token-2", "participant-1")));
    }

    private static ServerSession CreateSession(string path) =>
        new(NullLogger<ServerSession>.Instance, path);

    private sealed class TempSessionFile : IDisposable
    {
        private readonly string _directory;

        private TempSessionFile(string directory)
        {
            _directory = directory;
            Path = System.IO.Path.Combine(directory, "server-session.bin");
        }

        public string Path { get; }

        public static TempSessionFile Create()
        {
            var directory = System.IO.Path.Combine(
                System.IO.Path.GetTempPath(),
                "oeims-tests",
                Guid.NewGuid().ToString("N"));

            Directory.CreateDirectory(directory);

            return new TempSessionFile(directory);
        }

        public void Dispose()
        {
            if (Directory.Exists(_directory))
                Directory.Delete(_directory, recursive: true);
        }
    }
}
