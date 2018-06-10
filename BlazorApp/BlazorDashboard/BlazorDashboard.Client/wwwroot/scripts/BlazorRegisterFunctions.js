Blazor.registerFunction('blazor_chartistTest', () => {
    var data = {
        // A labels array that can contain any sort of values
        labels: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri'],
        // Our series array that contains series objects or in this case series data arrays
        series: [
            [5, 2, 4, 2, 0]
        ]
    };

    // Create a new line chart object where as first parameter we pass in a selector
    // that is resolving to our chart container element. The Second parameter
    // is the actual data object.
    new Chartist.Line('.ct-chart', data);
    return true;
});

Blazor.registerFunction('blazor_localStorageGetValue', (key) => {
    return localStorage.getItem(key);
});

Blazor.registerFunction('blazor_localStorageSetValue', (key, value) => {
    localStorage.setItem(key, value);
    return true;
});