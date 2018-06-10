using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace BlazorDashboard.Client.ApiModel
{
    public class IndicatorsModel
    {
        public decimal SMA { get; set; }
        public decimal EMA { get; set; }
        public string Date { get; set; }
        public DateTime DateDateTime => new DateTime(int.Parse(Date.Substring(0, 4)), int.Parse(Date.Substring(4, 2)), int.Parse(Date.Substring(6, 2)));
    }
}
