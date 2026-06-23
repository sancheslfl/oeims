using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class ServerApi(
    ServerConfig config,
    HttpClient httpClient)
{
    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    private readonly string _apiBaseUrl = config.ApiBaseUrl.TrimEnd('/');

    public async Task<JoinExchangeResponse> VerifyJoinTokenAsync(
        string emailJoinToken,
        CancellationToken ct)
    {
        var url = $"{_apiBaseUrl}/sessions/join/verify";

        using var response = await httpClient.PostAsJsonAsync(
            url,
            new JoinExchangeRequest(emailJoinToken),
            JsonOptions,
            ct);

        var body = await response.Content.ReadAsStringAsync(ct);

        if (!response.IsSuccessStatusCode)
            throw new InvalidOperationException(
                $"Sentinel authorization failed. Server returned {(int)response.StatusCode}: {body}");

        var join = JsonSerializer.Deserialize<JoinExchangeResponse>(
            body,
            JsonOptions);

        if (join is null)
            throw new InvalidOperationException(
                "Empty Sentinel authorization response.");

        if (string.IsNullOrWhiteSpace(join.Token))
            throw new InvalidOperationException(
                $"Sentinel authorization response is missing token. Body: {body}");

        if (string.IsNullOrWhiteSpace(join.ParticipantId))
            throw new InvalidOperationException(
                $"Sentinel authorization response is missing participantId. Body: {body}");

        return join;
    }
}

internal sealed record JoinExchangeRequest(
    [property: JsonPropertyName("token")]
    string Token);

internal sealed record JoinExchangeResponse(
    [property: JsonPropertyName("token")]
    string Token,

    [property: JsonPropertyName("participantId")]
    string ParticipantId);