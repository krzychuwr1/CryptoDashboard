using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace BlazorDashboard.Client.ApiModel
{
    public static class CurrencyCodes
    {
        public static List<string> CryptocurrenciesList { get; set; } = new List<string> { "ETH", "BTC" };
        public static List<string> CurrenciesList { get; set; } = new List<string> { "EUR", "USD" };
    }
}
