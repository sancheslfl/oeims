using Contracts;
using Microsoft.Extensions.DependencyInjection;
using OEIMS.Sentinel.Agent.Ipc;
using OEIMS.Sentinel.Agent.Mitigators;
using OEIMS.Sentinel.Agent.Monitors;

namespace OEIMS.Sentinel.Agent;

public static class AgentCoreServiceCollectionExtensions
{
    public static IServiceCollection AddAgentCore(this IServiceCollection services)
    {
        services.AddSingleton<AgentEventPipeClient>();
        services.AddSingleton<AgentCommandPipeServer>();

        services.AddSingleton<IMonitor, FocusMonitor>();
        services.AddSingleton<IMitigator, ClipboardBlocker>();

        services.AddHostedService<Worker>();

        return services;
    }
}