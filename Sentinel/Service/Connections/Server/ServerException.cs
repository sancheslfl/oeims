using System.Net;

namespace OEIMS.Sentinel.Service.Connections.Server;

internal sealed class ServerException(
    HttpStatusCode statusCode,
    string responseBody)
    : Exception($"Server returned {(int)statusCode}: {responseBody}")
{
    public HttpStatusCode StatusCode { get; } = statusCode;

    public string ResponseBody { get; } = responseBody;
}