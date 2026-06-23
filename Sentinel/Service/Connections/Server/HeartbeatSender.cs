using System.Net.Http.Headers;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class HeartbeatSender(
    ServerConfig config,
    ServerSession serverSession,
    HttpClient httpClient,
    ILogger<HeartbeatSender> logger)
{
    private static readonly TimeSpan Interval = TimeSpan.FromSeconds(30);

    private readonly string _apiBaseUrl = config.ApiBaseUrl.TrimEnd('/');

    public async Task StartAsync(CancellationToken ct)
    {
        logger.LogInformation("[Heartbeat] Waiting for Sentinel authorization");

        try
        {
            await serverSession.WaitUntilAuthorizedAsync(ct);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            return;
        }

        logger.LogInformation("[Heartbeat] Starting — every {Seconds}s",
            Interval.TotalSeconds);

        while (!ct.IsCancellationRequested)
        {
            await SendAsync(ct);

            try
            {
                await Task.Delay(Interval, ct);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task SendAsync(CancellationToken ct)
    {
        var authorization = serverSession.GetAuthorization();
        var url = $"{_apiBaseUrl}/participants/{authorization.ParticipantId}/heartbeat";

        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Post, url);

            request.Headers.Authorization =
                new AuthenticationHeaderValue("Bearer", authorization.Token);

            using var response = await httpClient.SendAsync(request, ct);

            if (response.IsSuccessStatusCode)
                logger.LogDebug("[Heartbeat] {StatusCode}", (int)response.StatusCode);
            else
                logger.LogWarning("[Heartbeat] Unexpected status {StatusCode}",
                    (int)response.StatusCode);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested) { }
        catch (Exception ex)
        {
            logger.LogWarning(ex, "[Heartbeat] Failed to reach server");
        }
    }
}