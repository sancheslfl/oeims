using System.Text.Json;

namespace Contracts.WebSocket
{
    public static class ServerMessageTypes
    {
        public const string ExamIdentityCode = "ExamIdentityCode";
    }

    public sealed record ServerMessage(string Type, JsonElement Data);

}
