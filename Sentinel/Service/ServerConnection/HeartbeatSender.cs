using System.Net.Http.Headers;

namespace OEIMS.Sentinel.Service.ServerConnection;

internal sealed class HeartbeatSender(
    ServerConfig config,
    HttpClient httpClient,
    ILogger<HeartbeatSender> logger)
{
    private static readonly TimeSpan Interval = TimeSpan.FromSeconds(30);

    private readonly string _url =
        $"{config.ApiBaseUrl.TrimEnd('/')}/participants/{config.ParticipantId}/heartbeat";

    public async Task RunAsync(CancellationToken ct)
    {
        logger.LogInformation("[Heartbeat] Starting — {Url} every {Seconds}s",
            _url, Interval.TotalSeconds);

        while (!ct.IsCancellationRequested)
        {
            await SendAsync(ct);

            try { await Task.Delay(Interval, ct); }
            catch (OperationCanceledException) { break; }
        }
    }

    private async Task SendAsync(CancellationToken ct)
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, _url);
            request.Headers.Authorization =
                new AuthenticationHeaderValue("Bearer", config.Token);

            using var response = await httpClient.SendAsync(request, ct);

            if (response.IsSuccessStatusCode)
                logger.LogDebug("[Heartbeat] {StatusCode}", (int)response.StatusCode);
            else
                logger.LogWarning("[Heartbeat] Unexpected status {StatusCode}", (int)response.StatusCode);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested) { }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "[Heartbeat] Failed to reach server");
        }
    }
}
