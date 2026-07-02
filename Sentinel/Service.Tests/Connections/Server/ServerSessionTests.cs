using Microsoft.Extensions.Logging.Abstractions;
using OEIMS.Sentinel.Service.Connections.Server;

namespace OEIMS.Sentinel.Service.Tests.Connections.Server;

public sealed class ServerSessionTests
{
    [Fact]
    public async Task Authorize_persists_authorization_and_releases_waiters()
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

    [Fact]
    public void Constructor_restores_persisted_authorization()
    {
        using var file = TempSessionFile.Create();

        CreateSession(file.Path).Authorize("token-1", "participant-1");

        var restored = CreateSession(file.Path);

        Assert.True(restored.IsAuthorized);
        Assert.Equal(
            new ServerAuthorization("token-1", "participant-1"),
            restored.GetAuthorization());
    }

    [Fact]
    public async Task Clear_removes_authorization_file_and_releases_change_waiters()
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

    [Fact]
    public async Task WaitUntilAuthorizationChangedAsync_completes_when_authorization_changes()
    {
        using var file = TempSessionFile.Create();
        var session = CreateSession(file.Path);

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var waitTask = session.WaitUntilAuthorizationChangedAsync(cts.Token);

        session.Authorize("token-1", "participant-1");

        await waitTask;
    }

    [Theory]
    [InlineData("", "participant-1", "token")]
    [InlineData("   ", "participant-1", "token")]
    [InlineData("token-1", "", "participantId")]
    [InlineData("token-1", "   ", "participantId")]
    public void Authorize_rejects_empty_values(
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

    [Fact]
    public void IsCurrent_returns_false_after_authorization_changes()
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
        new(
            NullLogger<ServerSession>.Instance,
            path,
            Protect,
            Unprotect);

    private static byte[] Protect(byte[] bytes) => bytes.Reverse().ToArray();

    private static byte[] Unprotect(byte[] bytes) => bytes.Reverse().ToArray();

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
                "oeims-service-tests",
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
