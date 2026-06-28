using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using OEIMS.Sentinel.Service.Connections.Server;
using System.Text.Json.Serialization;

namespace OEIMS.Sentinel.Service.Connections.WebClient;

/// <summary>
/// Exposes local loopback endpoint used by the Browser
/// to communicate with Sentinel Service.
/// </summary>
/// <remarks>
/// Communication:
/// <code>
/// Web Client -> Sentinel Service
/// </code>
/// </remarks>
internal static class LoopbackApi
{
    private const string AuthorizePath = "/sentinel/authorize";

    internal static WebApplication MapLoopbackApi(this WebApplication app)
    {
        var clientOrigin = app.Configuration["Loopback:ClientOrigin"];

        if (string.IsNullOrWhiteSpace(clientOrigin))
            throw new InvalidOperationException(
                "Missing Loopback:ClientOrigin configuration.");

        app.MapPost(AuthorizePath, async (
            AuthorizeRequest request,
            ServerApi serverApi,
            ServerSession serverSession,
            ILoggerFactory loggerFactory,
            CancellationToken ct) =>
        {
            var logger = loggerFactory.CreateLogger("LoopbackApi");
            var emailJoinToken = request.EmailJoinToken.Trim();

            if (emailJoinToken.Length == 0)
                return Results.BadRequest(new
                {
                    error = "Missing email join token."
                });

            try
            {
                var successfulJoin = await serverApi.VerifyJoinTokenAsync(
                    emailJoinToken,
                    ct);

                serverSession.Authorize(
                    token: successfulJoin.Token,
                    participantId: successfulJoin.ParticipantId);

                return Results.Ok(new
                {
                    status = "authorized"
                });
            }
            catch (ServerException ex)
            {
                logger.LogWarning(ex, "Sentinel authorization rejected by server.");

                return Results.Content(
                    ex.ResponseBody,
                    statusCode: (int)ex.StatusCode);
            }
            catch (Exception ex)
            {
                logger.LogWarning(ex, "Sentinel authorization failed.");

                return Results.Json(
                    new { error = ex.Message },
                    statusCode: StatusCodes.Status500InternalServerError);
            }
        })
        .RequireCors(policy =>
            policy
                .WithOrigins(clientOrigin)
                .WithMethods("POST")
                .WithHeaders("Content-Type"));

        return app;
    }
}

internal sealed record AuthorizeRequest(
    [property: JsonPropertyName("emailJoinToken")]
    string EmailJoinToken);