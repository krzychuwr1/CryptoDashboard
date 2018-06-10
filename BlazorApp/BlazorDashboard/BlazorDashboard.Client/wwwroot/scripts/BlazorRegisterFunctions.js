Blazor.registerFunction('blazor_chartist', (id, labels, series1, series2) => {
    var data = {
        labels: labels,
        series: [
            series1,
            series2
        ]
    };

    var options = {
        showPoint: true,
        // Disable line smoothing
        lineSmooth: false,
        axisX: {
            showGrid: true,
            showLabel: true
        },
        axisY: {
            offset: 60,
            labelInterpolationFnc: function (value) {
                return '$' + value;
            }
        }
    };

    new Chartist.Line('.ct-chart', data, options);
    return true;
});

Blazor.registerFunction('blazor_localStorageGetValue', (key) => {
    return localStorage.getItem(key);
});

Blazor.registerFunction('blazor_localStorageSetValue', (key, value) => {
    localStorage.setItem(key, value);
    return true;
});