using Daemon.Domain;

namespace Daemon.Platform.Windows;

public static class WinPlatformServiceCollectionExtensions
{
    public static IServiceCollection AddWindowsPlatform(this IServiceCollection services)
    {
        services.AddSingleton<IActiveWindowSource, WindowsActiveWindowSource>();

        return services;
    }
}