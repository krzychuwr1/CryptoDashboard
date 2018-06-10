using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;

namespace BlazorDashboard.Client.ApiModel
{
    public class SingleDailyInfo
    {
        public DateTime Date => new DateTime(int.Parse(time.Substring(0, 4)), int.Parse(time.Substring(4,2)), int.Parse(time.Substring(6,2)));
        public string time { get; set; }
        public decimal close { get; set; }
        public decimal high { get; set; }
        public decimal low { get; set; }
        public decimal open { get; set; }
        public decimal volumefrom { get; set; }
        public decimal volumeto { get; set; }
        public string fromSymbol { get; set; }
        public string toSymbol { get; set; }
    }
}
