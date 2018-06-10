Blazor.registerFunction('blazor_chartistTest', (low, high) => {
    // Our labels and three data series
    var data = {
        labels: ['Week1', 'Week2', 'Week3', 'Week4', 'Week5', 'Week6', 'Week7'],
        series: [
            low,
            high
        ]
    };

    // We are setting a few options for our chart and override the defaults
    var options = {
        // Don't draw the line chart points
        showPoint: true,
        // Disable line smoothing
        lineSmooth: false,
        // X-Axis specific configuration
        axisX: {
            // We can disable the grid for this axis
            showGrid: true,
            // and also don't show the label
            showLabel: true
        },
        // Y-Axis specific configuration
        axisY: {
            // Lets offset the chart a bit from the labels
            offset: 60,
            // The label interpolation function enables you to modify the values
            // used for the labels on each axis. Here we are converting the
            // values into million pound.
            labelInterpolationFnc: function (value) {
                return '$' + value;
            }
        }
    };

    // All you need to do is pass your configuration as third parameter to the chart function
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