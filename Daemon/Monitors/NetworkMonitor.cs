using System;
using System.Collections.Generic;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Text;

namespace Daemon.Monitors
{
    internal class NetworkMonitor
    {
        private string? _initialNetworkId;
        private HashSet<string> _initialInterfaces = new();

        public void InitializeBaseline()
        {
            /*if (!IsValidNetworkState())
            {
                // BlockExam() -> blocking function that quits when network turns valid

            }*/
            _initialNetworkId = GetCurrentNetworkId();
            _initialInterfaces = GetActiveInterfaces();
        }

        public bool HasNetworkChanged()
        {
            var current = GetCurrentNetworkId();
            return current != _initialNetworkId;
        }

        public bool HasMultipleInterfaces()
        {
            var current = GetActiveInterfaces();
            return !_initialInterfaces.SetEquals(current);
        }

        public bool HasMultipleActiveNetworks()
        {
            var count = GetActiveInterfaces().Count;
            return count > 1;
        }

        public bool HasNoActiveNetworks()
        {
            var count = GetActiveInterfaces().Count;
            return count == 0;
        }

        public bool IsValidNetworkState()
        {
            return !HasNoActiveNetworks() && !HasMultipleActiveNetworks() && !HasMultipleInterfaces();
        }

        private string GetCurrentNetworkId()
        {
            var active = NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                            n.NetworkInterfaceType != NetworkInterfaceType.Loopback);

            return string.Join("|", active.Select(n =>
            {
                var ipProps = n.GetIPProperties();
                var gateway = ipProps.GatewayAddresses
                    .FirstOrDefault()?.Address.ToString() ?? "no-gw";

                return $"{n.Name}-{gateway}";
            }));
        }

        private HashSet<string> GetActiveInterfaces()
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(n =>
                        n.OperationalStatus == OperationalStatus.Up &&
                        n.NetworkInterfaceType != NetworkInterfaceType.Loopback &&
                        n.GetIPProperties().GatewayAddresses.Any()
                )
                .Select(n => n.Name)
                .ToHashSet();
        }
    }
}