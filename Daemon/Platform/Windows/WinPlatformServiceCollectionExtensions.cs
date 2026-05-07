using Daemon.Domain;

namespace Daemon.Platform.Windows;

public static class WinPlatformServiceCollectionExtensions
{
    public static IServiceCollection AddWindowsPlatform(this IServiceCollection services)
    {
        services.AddSingleton<IProcessSource, WindowsProcessSource>();
        services.AddSingleton<IActiveWindowSource, WinActiveWindowSource>();

        return services;
    }
}